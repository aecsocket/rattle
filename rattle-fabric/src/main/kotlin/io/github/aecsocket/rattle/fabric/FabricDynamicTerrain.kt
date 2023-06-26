package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.alexandria.extension.swapList
import io.github.aecsocket.alexandria.fabric.ItemDisplayRender
import io.github.aecsocket.alexandria.fabric.extension.nextEntityId
import io.github.aecsocket.alexandria.fabric.extension.toDVec
import io.github.aecsocket.alexandria.sync.Locked
import io.github.aecsocket.klam.DIso3
import io.github.aecsocket.klam.DVec3
import io.github.aecsocket.klam.IVec3
import io.github.aecsocket.rattle.Compound
import io.github.aecsocket.rattle.PhysicsSpace
import io.github.aecsocket.rattle.world.DynamicTerrain
import io.github.aecsocket.rattle.world.TILES_IN_SLICE
import io.github.aecsocket.rattle.world.posInChunk
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.ChunkStatus

class FabricDynamicTerrain(
    platform: FabricRattlePlatform,
    physics: PhysicsSpace,
    val world: ServerLevel,
    settings: Settings = Settings(),
) : DynamicTerrain(platform, physics, settings) {
    private val toSnapshot = Locked(HashSet<IVec3>())
    private val layerByBlock = HashMap<Block, Int>()

    override fun createRender(pos: IVec3) = ItemDisplayRender(nextEntityId()) { packet ->
        PlayerLookup
            .tracking(world, ChunkPos(pos.x, pos.z))
            .filter { it.rattle().draw.terrain }
            .forEach { it.connection.send(packet) }
    }

    override fun scheduleSnapshot(pos: IVec3) {
        toSnapshot.withLock { it += pos }
    }

    fun onTick() {
        toSnapshot.withLock { it.swapList() }.forEach { pos ->
            val snapshot = createSnapshot(world.getChunk(pos.x, pos.z, ChunkStatus.FULL), pos)
            setSliceSnapshot(pos, snapshot)
        }
    }

    private fun createSnapshot(chunk: ChunkAccess, pos: IVec3): SliceState.Snapshot {
        if (pos.y < -world.minBuildHeight / 16 || pos.y >= world.maxBuildHeight / 16) {
            return SliceState.Snapshot.Empty
        }
        if (chunk.getSection(pos.y + world.minBuildHeight / 16).hasOnlyAir()) {
            return SliceState.Snapshot.Empty
        }

        val tiles: Array<out Tile?> = Array(TILES_IN_SLICE) { i ->
            val (gx, gy, gz) = pos * 16 + posInChunk(i)
            // guaranteed to be in range because of the Y check at the start
            val blockPos = BlockPos(gx, gy, gz)
            wrapBlock(chunk.getBlockState(blockPos), blockPos)
        }

        println("$pos -> ${tiles.count { it != null }}")
        return SliceState.Snapshot(
            tiles = tiles,
        )
    }

    private fun wrapBlock(block: BlockState, pos: BlockPos): Tile? {
        fun layerId(default: Int) = layerByBlock.computeIfAbsent(block.block) {
            settings.layers.byBlock[BuiltInRegistries.BLOCK.getKey(block.block)]?.let { layerKey ->
                layerByKey[layerKey]
            } ?: default
        }

        val collShape = block.getCollisionShape(world, pos)
        when {
            // TODO isLiquid
            collShape.isEmpty -> {
                return null
            }
            else -> {
                val shapes = collShape.toAabbs()
                    .map { box ->
                        Compound.Child(
                            shape = boxShape(DVec3(
                                box.maxX - box.minX,
                                box.maxY - box.minY,
                                box.maxZ - box.minZ,
                            ) / 2.0),
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
}
