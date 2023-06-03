package io.github.aecsocket.rattle.paper

import io.github.aecsocket.alexandria.paper.extension.toDVec
import io.github.aecsocket.klam.IVec2
import io.github.aecsocket.klam.IVec3
import io.github.aecsocket.klam.xz
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.world.BLOCKS_IN_SECTION
import io.github.aecsocket.rattle.world.DynamicTerrain
import io.github.aecsocket.rattle.world.posInChunk
import org.bukkit.Chunk
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.data.BlockData
import org.bukkit.util.Vector
import java.util.concurrent.ConcurrentHashMap

typealias PaperBlock = org.bukkit.block.Block

private val onlyPassable = Array(BLOCKS_IN_SECTION) { Block.Passable }

class PaperDynamicTerrain(
    private val rattle: PaperRattle,
    physics: PhysicsSpace,
    private val world: World,
) : DynamicTerrain(rattle.rattle, physics) {
    private val negativeYSections = -world.minHeight / 16
    private val ySlices = (world.maxHeight - world.minHeight) / 16
    private val shapeCache = ConcurrentHashMap<BlockData, Shape?>()

    // SAFETY: scheduleToSnapshot will only be called sequentially, so byXZ will no longer be in use
    // by the next time the method is invoked
    private val byXZ = HashMap<IVec2, MutableSet<Int>>()

    override fun destroy() {
        super.destroy()

        val dangling = ArrayList<Shape>()
        shapeCache.forEach { (_, shape) ->
            if (shape == null) return@forEach
            // should be 1 by this point, since we give out the shapes and cache them, and let anyone else use them
            // and `super.destroy()` should have destroyed all the colliders that use our shapes,
            // thereby releasing them
            // if the terrain strategy screws up and double-frees, then our code here is unsafe
            // but in that case, we have bigger issues than this
            if (shape.refCount == 1L) {
                shape.release()
                // we can't check the refCount after it's released, since by that point it's... uh... released
                // it's not safe to read its state anymore
            } else {
                dangling += shape
            }
        }

        if (dangling.isNotEmpty()) {
            val log = rattle.log
            val take = 4
            log.warn { "Found dangling shape references after world destruction:" }
            dangling.take(take).forEach { shape ->
                log.warn { "  - $shape with ${shape.refCount} refs" }
            }
            if (dangling.size > take) {
                log.warn { "  ...and ${dangling.size - take} more" }
            }
        }
    }

    override fun scheduleToSnapshot(sectionPos: Iterable<IVec3>) {
        byXZ.clear()
        sectionPos.forEach { pos ->
            byXZ.computeIfAbsent(pos.xz) { HashSet() } += pos.y
        }

        byXZ.forEach { (xz, ys) ->
            if (!world.isChunkLoaded(xz.x, xz.y)) return@forEach
            rattle.scheduling.onChunk(world, xz).launch {
                val chunk = world.getChunkAt(xz.x, xz.y)

                val snapshots = ys.map { y ->
                    val pos = IVec3(xz.x, y, xz.y)
                    pos to createSnapshot(chunk, pos)
                }
                sections.withLock { sections ->
                    snapshots.forEach snapshot@ { (pos, snapshot) ->
                        // illegal states (null or wrong state) will fail silently
                        val section = sections[pos] ?: return@snapshot
                        when (section.state) {
                            is SectionState.Pending -> {
                                section.state = snapshot
                                sections.dirty(pos)
                            }
                            else -> { /* unexpected state, fail silently */ }
                        }
                    }
                }
            }
        }
    }

    private fun createSnapshot(chunk: Chunk, pos: IVec3): SectionState.Snapshot {
        val iy = pos.y + negativeYSections

        // TODO: https://github.com/PaperMC/Paper/issues/9255
        /*
        Paper:
        public ChunkAccess getHandle(ChunkStatus chunkStatus)

        Folia:
        public net.minecraft.world.level.chunk.LevelChunk getHandle()
         */
//        val sections = (chunk as CraftChunk).handle.sections
//        if (iy < 0 || iy >= sections.size || sections[iy].hasOnlyAir()) {
//            return SectionState.Snapshot(onlyPassable)
//        }
        if (iy < 0 || iy >= ySlices) {
            return SectionState.Snapshot(onlyPassable)
        }

        val blocks = Array(BLOCKS_IN_SECTION) { i ->
            val (lx, ly, lz) = posInChunk(i)
            // getBlock indexes xz by 0-15, but y is global
            val gy = pos.y * 16 + ly
            wrapBlock(chunk.getBlock(lx, gy, lz))
        }

        return SectionState.Snapshot(
            blocks = blocks,
        )
    }

    private fun wrapBlock(block: PaperBlock): Block {
        if (block.isPassable) {
            return Block.Passable
        }

        val shape = shapeCache.computeIfAbsent(block.blockData) {
            val boxes = block.collisionShape.boundingBoxes
            when {
                boxes.isEmpty() -> null
                boxes.size == 1 && boxes.first().center == Vector(0.5, 0.5, 0.5) -> {
                    val box = boxes.first()
                    val halfExtent = (box.max.toDVec() - box.min.toDVec()) / 2.0
                    box(halfExtent)
                }
                else -> {
                    val children = boxes.map { box ->
                        val center = box.center.toDVec() - 0.5
                        val halfExtent = (box.max.toDVec() - box.min.toDVec()) / 2.0
                        Compound.Child(
                            // SAFETY: the compound will own this shape, so when it's dropped, so is this box ref
                            shape = box(halfExtent),
                            delta = Iso(center)
                        )
                    }
                    rattle.engine.createShape(Compound(children))
                }
            }
        } ?: return Block.Passable

        return when (block.type) {
            Material.WATER, Material.LAVA -> Block.Fluid(shape)
            else -> Block.Solid(shape)
        }
    }

    private fun box(halfExtent: Vec): Shape {
        // todo maybe this should be cached to reduce memory?? ehh idk
        // if we do decide to cache, make sure to acquire a ref as well
        return rattle.engine.createShape(Box(halfExtent))
    }
}
