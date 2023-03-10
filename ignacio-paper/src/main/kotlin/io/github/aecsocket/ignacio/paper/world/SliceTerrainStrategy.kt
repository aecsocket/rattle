package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.math.*
import io.github.aecsocket.ignacio.paper.ignacioBodyName
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.bukkit.Chunk
import org.bukkit.ChunkSnapshot
import org.bukkit.World

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

    private val bodyKey = ignacioBodyName("SliceTerrainStrategy-${world.name}")
    private val cube = engine.createShape(BoxGeometry(Vec3f(0.5f)))
    private val startY = world.minHeight
    private val numSlices = (world.maxHeight - startY) / 16
    private val negativeYSlices = -startY / 16

    private val chunkSnapshots = HashMap<Long, ChunkSnapshot>()
    private var toRemove: MutableSet<SlicePos> = HashSet()
    private var toCreate: MutableSet<SlicePos> = HashSet()
    private var toSnapshot: MutableMap<SlicePos, SliceSnapshot> = HashMap()

    private val sliceData = HashMap<SlicePos, SliceData>()
    private val bodyToSlice = HashMap<PhysicsBody, SliceData>()

    var enabled = true
        private set

    override fun destroy() {
        cube.destroy()
    }

    override fun enable() {
        enabled = true
    }

    override fun disable() {
        enabled = false
    }

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
                name = "$bodyKey-${slice.pos}",
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
        if (!enabled) return

        // create snapshots for positions
        val toSnapshot = HashMap<SlicePos, SliceSnapshot>()
        toCreate.forEach { pos ->
            // chunk not loaded, don't load it
            if (!world.isChunkLoaded(pos.x, pos.z)) return@forEach
            // already created body, don't remake it
            if (sliceData.contains(pos)) return@forEach
            val sy = pos.y + negativeYSlices
            // out of range, we can't make a body
            if (sy < 0 || sy >= numSlices) return@forEach

            val chunkKey = Chunk.getChunkKey(pos.x, pos.z)
            val snapshot = chunkSnapshots.computeIfAbsent(chunkKey) {
                world.getChunkAt(pos.x, pos.z).getChunkSnapshot(false, false, false)
            }
            // empty slices aren't even passed to toCreate
            if (snapshot.isSectionEmpty(sy)) return@forEach

            toSnapshot[pos] = SliceSnapshot(
                pos = pos,
                blocks = snapshot,
            )
        }
        chunkSnapshots.clear()
        // even though we're double buffering, we clear it afterwards anyway
        // in case we go through a 2nd tick and the buffer hasn't updated yet
        toCreate.clear()
        this.toSnapshot = toSnapshot

        engine.launchTask {
            // clear all the bodies we've marked as unused last tick
            val bodiesToRemove = synchronized(sliceData) {
                synchronized(bodyToSlice) {
                    toRemove.mapNotNull { pos ->
                        sliceData.remove(pos)?.body?.also {
                            bodyToSlice -= it
                        }
                    }
                }
            }
            toRemove.clear()
            physics.bodies {
                removeAll(bodiesToRemove)
                destroyAll(bodiesToRemove)
            }

            // create all the bodies we've created snapshots for last tick
            val slices = toSnapshot.map { (_, slice) ->
                async { createSliceData(slice) }
            }.awaitAll()
            toSnapshot.clear()

            val bodiesToAdd = ArrayList<PhysicsBody>()
            synchronized(sliceData) {
                synchronized(bodyToSlice) {
                    slices.forEach { data ->
                        sliceData[data.pos] = data
                        data.body?.let {
                            bodyToSlice[it] = data
                            bodiesToAdd += it
                        }
                    }
                }
            }
            physics.bodies.addAll(bodiesToAdd, false)
        }
    }

    override fun physicsUpdate(deltaTime: Float) {
        if (!enabled) return

        val toRemove = synchronized(sliceData) { HashSet(sliceData.keys) }
        val toCreate = HashSet<SlicePos>()
        physics.bodies.active().forEach { body ->
            body.readUnlocked { access ->
                // only create for moving objects (TODO custom object layer support: expand this)
                if (access.objectLayer != engine.layers.ofObject.moving) return@readUnlocked
                // next tick, we will create snapshots of the chunk slices this body covers
                val overlappingSlices = (access.boundingBox / 16.0).points().toSet()
                toCreate += overlappingSlices
                toRemove -= overlappingSlices
            }
        }
        this.toRemove = toRemove
        this.toCreate = toCreate
    }

    override fun isTerrain(body: PhysicsBody) = synchronized(bodyToSlice) { bodyToSlice.contains(body) }

    override fun onChunksLoad(chunks: Collection<Chunk>) {}

    override fun onChunksUnload(chunks: Collection<Chunk>) {
        if (!enabled) return

    }

    override fun onBlocksUpdate(blocks: Collection<BlockPos>) {
        if (!enabled) return

    }
}
