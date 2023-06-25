package io.github.aecsocket.rattle.paper

import io.github.aecsocket.alexandria.paper.ChunkTracking
import io.github.aecsocket.alexandria.paper.ItemDisplayRender
import io.github.aecsocket.alexandria.paper.extension.nextEntityId
import io.github.aecsocket.alexandria.paper.extension.sendPacket
import io.github.aecsocket.alexandria.paper.extension.toDVec
import io.github.aecsocket.alexandria.paper.packetReceiver
import io.github.aecsocket.klam.*
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.world.DynamicTerrain
import io.github.aecsocket.rattle.world.TILES_IN_SLICE
import io.github.aecsocket.rattle.world.posInChunk

import org.bukkit.Chunk
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Player

/*
all NMS SoundType's:
    WOOD
    GRAVEL
    GRASS
    LILY_PAD
    STONE
    METAL
    GLASS
    WOOL
    SAND
    SNOW
    POWDER_SNOW
    LADDER
    ANVIL
    SLIME_BLOCK
    HONEY_BLOCK
    WET_GRASS
    CORAL_BLOCK
    BAMBOO
    BAMBOO_SAPLING
    SCAFFOLDING
    SWEET_BERRY_BUSH
    CROP
    HARD_CROP
    VINE
    NETHER_WART
    LANTERN
    STEM
    NYLIUM
    FUNGUS
    ROOTS
    SHROOMLIGHT
    WEEPING_VINES
    TWISTING_VINES
    SOUL_SAND
    SOUL_SOIL
    BASALT
    WART_BLOCK
    NETHERRACK
    NETHER_BRICKS
    NETHER_SPROUTS
    NETHER_ORE
    BONE_BLOCK
    NETHERITE_BLOCK
    ANCIENT_DEBRIS
    LODESTONE
    CHAIN
    NETHER_GOLD_ORE
    GILDED_BLACKSTONE
    CANDLE
    AMETHYST
    AMETHYST_CLUSTER
    SMALL_AMETHYST_BUD
    MEDIUM_AMETHYST_BUD
    LARGE_AMETHYST_BUD
    TUFF
    CALCITE
    DRIPSTONE_BLOCK
    POINTED_DRIPSTONE
    COPPER
    CAVE_VINES
    SPORE_BLOSSOM
    AZALEA
    FLOWERING_AZALEA
    MOSS_CARPET
    PINK_PETALS
    MOSS
    BIG_DRIPLEAF
    SMALL_DRIPLEAF
    ROOTED_DIRT
    HANGING_ROOTS
    AZALEA_LEAVES
    SCULK_SENSOR
    SCULK_CATALYST
    SCULK
    SCULK_VEIN
    SCULK_SHRIEKER
    GLOW_LICHEN
    DEEPSLATE
    DEEPSLATE_BRICKS
    DEEPSLATE_TILES
    POLISHED_DEEPSLATE
    FROGLIGHT
    FROGSPAWN
    MANGROVE_ROOTS
    MUDDY_MANGROVE_ROOTS
    MUD
    MUD_BRICKS
    PACKED_MUD
    HANGING_SIGN
    NETHER_WOOD_HANGING_SIGN
    BAMBOO_WOOD_HANGING_SIGN
    BAMBOO_WOOD
    NETHER_WOOD
    CHERRY_WOOD
    CHERRY_SAPLING
    CHERRY_LEAVES
    CHERRY_WOOD_HANGING_SIGN
    CHISELED_BOOKSHELF
    SUSPICIOUS_SAND
    DECORATED_POT
 */

class PaperDynamicTerrain(
    private val rattle: PaperRattle,
    world: World,
    physics: PhysicsSpace,
    settings: Settings = Settings(),
) : DynamicTerrain<World>(world, rattle.platform, physics, settings) {
    private val yIndices = (world.minHeight / 16) until (world.maxHeight / 16)
    private val layerByBlock = HashMap<Material, Int>()

    override fun createRender(pos: IVec3) = ItemDisplayRender(nextEntityId()) { packet ->
        ChunkTracking
            .trackedPlayers(world, pos.xz)
            .filter { rattle.playerData(it).draw.terrain }
            .forEach { it.sendPacket(packet) }
    }

    override fun scheduleSnapshot(pos: IVec3) {
        val chunk = world.getChunkAt(pos.x, pos.z)
        rattle.scheduling.onChunk(chunk).runLater {
            val snapshot = createSnapshot(chunk, pos)
            slices.withLock { slices ->
                val slice = slices[pos] ?: return@withLock
                when (slice.state) {
                    is SliceState.PendingSnapshot -> {
                        // if `snapshot` is null, it means that slice had no data (it may do in the future though)
                        // in which case we just mark it as "built" and have it hold no colliders
                        slice.state = snapshot ?: SliceState.Built
                        slices.dirty(pos)
                    }
                    else -> {
                        // silently fail
                    }
                }
            }
        }
    }

    private fun createSnapshot(chunk: Chunk, pos: IVec3): SliceState.Snapshot? {
        // TODO if chunk is empty, we don't snapshot
        if (pos.y < -world.minHeight / 16 || pos.y >= world.maxHeight / 16) {
            return null
        }

        val tiles: Array<out Tile?> = Array(TILES_IN_SLICE) { i ->
            val (lx, ly, lz) = posInChunk(i)
            val gy = pos.y * 16 + ly
            // guaranteed to be in range because of the Y check at the start
            val block = chunk.getBlock(lx, gy, lz)
            wrapBlock(block)
        }

        return SliceState.Snapshot(
            tiles = tiles,
        )
    }

    private fun wrapBlock(block: Block): Tile? {
        fun layerId(default: Int) = layerByBlock.computeIfAbsent(block.type) {
            settings.layers.byBlock[block.type.key]?.let { layerKey ->
                layerByKey[layerKey]
            } ?: default
        }

        when {
            block.isLiquid -> {
                val shape = Compound.Child(
                    shape = boxShape(DVec3(0.5)),
                )
                return Tile(
                    layerId = layerId(defaultFluidLayer),
                    shapes = listOf(shape),
                )
            }
            block.isPassable -> {
                return null
            }
            else -> {
                val shapes = block.collisionShape.boundingBoxes
                    .map { box ->
                        Compound.Child(
                            shape = boxShape(box.max.subtract(box.min).toDVec() / 2.0),
                            delta = DIso3(box.center.toDVec() - 0.5),
                        )
                    }
                return Tile(
                    layerId = layerId(defaultSolidLayer),
                    shapes = shapes,
                )
            }
        }
    }

    private fun boxShape(halfExtent: DVec3): Shape {
        // todo cache this?
        return rattle.engine.createShape(Box(halfExtent))
    }

    private fun runForSlicesIn(chunk: Chunk, fn: (Slice) -> Unit) {
        rattle.runTask {
            slices.withLock { slices ->
                yIndices.forEach { sy ->
                    val slice = slices[IVec3(chunk.x, sy, chunk.z)] ?: return@forEach
                    fn(slice)
                }
            }
        }
    }

    fun onTrackChunk(player: Player, chunk: Chunk) {
        runForSlicesIn(chunk) { slice ->
            slice.debugRenders.forEach {
                slice.onTrack((it.render as ItemDisplayRender).withReceiver(player.packetReceiver()), it)
            }
        }
    }

    fun onUntrackChunk(player: Player, chunk: Chunk) {
        runForSlicesIn(chunk) { slice ->
            slice.debugRenders.forEach {
                slice.onUntrack((it.render as ItemDisplayRender).withReceiver(player.packetReceiver()))
            }
        }
    }
}
