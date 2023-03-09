package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.math.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.bukkit.Chunk
import org.bukkit.ChunkSnapshot
import org.bukkit.World
import java.util.concurrent.atomic.AtomicBoolean

// note: negative slice Y coordinates are possible
typealias SlicePos = Point3

class SliceTerrainStrategy(
    private val engine: IgnacioEngine,
    private val world: World,
    private val physics: PhysicsSpace,
) : TerrainStrategy {
    data class SliceSnapshot(
        val pos: SlicePos,
        val blocks: ChunkSnapshot,
    )

    data class SliceData(
        val pos: SlicePos,
        val body: PhysicsBody?,
    )

    private val cube = engine.createShape(BoxGeometry(Vec3f(0.5f)))
    private val startY = world.minHeight
    private val numSlices = (world.maxHeight - startY) / 16
    private val negativeYSlices = -startY / 16

    private val updating = AtomicBoolean(false)
    private val toSnapshot = HashSet<SlicePos>()
    private val toCreate = HashMap<SlicePos, SliceSnapshot>()
    private val toRemove = HashSet<SlicePos>()
    private val chunkSnapshots = HashMap<Long, ChunkSnapshot>()
    private val bodyToSlice = HashMap<PhysicsBody, SliceData>()

    private val sliceData = HashMap<SlicePos, SliceData>()

    override fun destroy() {
        cube.destroy()
    }

    private fun startUpdating() = updating.compareAndSet(false, true)

    private fun endUpdating() = updating.set(false)

    private fun createSliceData(slice: SliceSnapshot): SliceData {
        val solidChildren = ArrayList<CompoundChild>()

        fun processBlock(lx: Int, ly: Int, lz: Int) {
            val gy = slice.pos.y * 16 + ly
            val block = slice.blocks.getBlockData(lx, gy, lz)
            if (block.material.isCollidable) {
                solidChildren += CompoundChild(
                    shape = cube,
                    position = Vec3f(lx + 0.5f, ly + 0.5f, lz + 0.5f),
                    rotation = Quat.Identity,
                )
            }
        }

        repeat(16) { lx ->
            repeat(16) { ly ->
                repeat(16) { lz ->
                    processBlock(lx, ly, lz)
                }
            }
        }

        if (solidChildren.isNotEmpty()) {
            val shape = engine.createShape(StaticCompoundGeometry(solidChildren))
            val body = physics.bodies.createStatic(StaticBodySettings(
                shape = shape,
                layer = engine.layers.ofObject.terrain,
            ), Transform(slice.pos.toVec3d() * 16.0))
            return SliceData(
                pos = slice.pos,
                body = body.body,
            )
        }
        return SliceData(
            pos = slice.pos,
            body = null,
        )
    }

    override fun tickUpdate() {
        if (startUpdating()) {
            toSnapshot.forEach { pos ->
                // don't load chunks if they're not loaded yet
                if (!world.isChunkLoaded(pos.x, pos.z)) return@forEach
                // already created body, no need to recreate
                if (sliceData.contains(pos)) return@forEach
                val sy = pos.y + negativeYSlices
                // out of range
                if (sy < 0 || sy >= numSlices) return@forEach

                val chunkKey = Chunk.getChunkKey(pos.x, pos.z)
                val snapshot = chunkSnapshots.computeIfAbsent(chunkKey) {
                    world.getChunkAt(pos.x, pos.z).getChunkSnapshot(false, false, false)
                }
                // empty slices aren't even passed to toCreate
                if (snapshot.isSectionEmpty(sy)) return@forEach

                toCreate[pos] = SliceSnapshot(
                    pos = pos,
                    blocks = snapshot,
                )
            }
            toSnapshot.clear()
            chunkSnapshots.clear()

            endUpdating()
        }

        engine.launchTask {
            if (startUpdating()) {
                // clear all the bodies we've marked as unused last tick
                val bodiesToRemove = toRemove.mapNotNull { pos -> sliceData[pos]?.body }
                physics.bodies {
                    removeAll(bodiesToRemove)
                    destroyAll(bodiesToRemove)
                }
                toRemove.clear()

                // create all the bodies we've generated snapshots for last tick
                val slices = toCreate.map { (_, slice) ->
                    async { createSliceData(slice) }
                }.awaitAll()
                val bodiesToAdd = ArrayList<PhysicsBody>()
                slices.forEach { data ->
                    sliceData[data.pos] = data
                    data.body?.let {
                        bodyToSlice[it] = data
                        bodiesToAdd += it
                    }
                }
                physics.bodies.addAll(bodiesToAdd, false)
                toCreate.clear()

                endUpdating()
            }
        }
    }

    override fun physicsUpdate(deltaTime: Float) {
        if (startUpdating()) {
            // mark all the chunk slices we need to generate
            physics.bodies.active().forEach { body ->
                body.readUnlocked { access ->
                    // only generate for moving objects (TODO custom object layer support: expand this)
                    if (access.objectLayer != engine.layers.ofObject.moving) return@readUnlocked
                    // next tick, we will create snapshots of the chunk slices this body covers
                    val overlappingSlices = (access.boundingBox / 16.0).points()
                    toSnapshot += overlappingSlices
                }
            }
            endUpdating()
        }
    }

    override fun isTerrain(body: PhysicsBody) = bodyToSlice.contains(body)

    override fun onChunksLoad(chunks: Collection<Chunk>) {}

    override fun onChunksUnload(chunks: Collection<Chunk>) {

    }

    override fun onBlocksUpdate(blocks: Collection<BlockPos>) {

    }
}
