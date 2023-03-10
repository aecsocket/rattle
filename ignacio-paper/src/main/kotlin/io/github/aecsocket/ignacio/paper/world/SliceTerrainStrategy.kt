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
    private val movingFilter = engine.filters.createBroad(
        broad = { layer -> layer == engine.layers.ofBroadPhase.moving },
        objects = { true }
    )
    private val startY = world.minHeight
    private val numSlices = (world.maxHeight - startY) / 16
    private val startYSlices = -startY / 16

    private var toRemove: MutableSet<SlicePos> = HashSet()
    private var toCreateSnapshotOf: MutableSet<SlicePos> = HashSet()
    private val toUpdate = HashSet<SlicePos>()

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

    private fun createGeometry(slice: SliceSnapshot): Geometry? {
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

        return if (solidChildren.isNotEmpty()) {
            StaticCompoundGeometry(solidChildren)
        } else null
    }

    private fun createSliceData(slice: SliceSnapshot): SliceData {
        return createGeometry(slice)?.let { solidGeometry ->
            val shape = engine.createShape(solidGeometry)
            val body = physics.bodies.createStatic(StaticBodySettings(
                name = "$bodyKey-${slice.pos}",
                shape = shape,
                layer = engine.layers.ofObject.terrain,
            ), Transform(slice.pos.toVec3d() * 16.0))
            SliceData(
                pos = slice.pos,
                body = body.body,
            )
        } ?: SliceData(pos = slice.pos, body = null)
    }

    private fun isValidPosition(pos: SlicePos): Boolean {
        if (!world.isChunkLoaded(pos.x, pos.z))
            return false
        val sy = pos.y + startYSlices
        if (sy < 0 || sy >= numSlices)
            return false
        return true
    }

    override fun tickUpdate() {
        if (!enabled) return

        // create snapshots for positions
        val toCreateFromSnapshot = HashMap<SlicePos, SliceSnapshot>()
        val chunkSnapshots = HashMap<Long, ChunkSnapshot>()
        toCreateSnapshotOf.forEach { pos ->
            if (!isValidPosition(pos)) return@forEach
            if (sliceData.contains(pos)) return@forEach

            val chunkKey = Chunk.getChunkKey(pos.x, pos.z)
            val blocks = chunkSnapshots.computeIfAbsent(chunkKey) {
                world.getChunkAt(pos.x, pos.z).getChunkSnapshot(false, false, false)
            }
            if (blocks.isSectionEmpty(pos.y + startYSlices)) return@forEach

            toCreateFromSnapshot[pos] = SliceSnapshot(
                pos = pos,
                blocks = blocks,
            )
        }
        // even though we're double buffering, we clear it afterwards anyway
        // in case we go through a 2nd tick and the buffer hasn't updated yet
        toCreateSnapshotOf.clear()

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
            val slices = toCreateFromSnapshot.map { (_, slice) ->
                async { createSliceData(slice) }
            }.awaitAll()
            toCreateFromSnapshot.clear()

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
        this.toCreateSnapshotOf = toCreate
    }

    override fun isTerrain(body: PhysicsBody) = synchronized(bodyToSlice) { bodyToSlice.contains(body) }

    override fun onChunksLoad(chunks: Collection<Chunk>) {}

    override fun onChunksUnload(chunks: Collection<Chunk>) {
        if (!enabled) return
    }

    override fun onSlicesUpdate(slices: Collection<SlicePos>) {
        if (!enabled) return
        slices.forEach { pos ->
            if (!isValidPosition(pos)) return@forEach

            engine.launchTask {
                val nearbyBodies = physics.broadQuery.overlapAABox(
                    box = AABB((pos * 16).toVec3d(), ((pos + 1) * 16).toVec3d()),
                    filter = movingFilter,
                )
                // there are no physics bodies in this slice, don't bother creating/updating it
                if (nearbyBodies.isEmpty()) return@launchTask

                synchronized(toUpdate) { toUpdate += pos }

                /*

            if (synchronized(sliceData) { !sliceData.contains(pos) }) return@forEach
            if (!world.isChunkLoaded(pos.x, pos.z)) return@forEach
            val sy = pos.y + negativeYSlices
            if (sy < 0 || sy >= numSlices) return@forEach

            val chunkKey = Chunk.getChunkKey(pos.x, pos.z)
            val blocks = chunkSnapshots.computeIfAbsent(chunkKey) {
                world.getChunkAt(pos.x, pos.z).getChunkSnapshot(false, false, false)
            }
            if (blocks.isSectionEmpty(sy)) return@forEach

            val snapshot = SliceSnapshot(
                pos = pos,
                blocks = blocks,
            )

            engine.launchTask {
                val slice = synchronized(sliceData) { sliceData[pos] } ?: return@launchTask
                slice.body?.write { body ->
                    createGeometry(snapshot)?.let { solidGeometry ->
                        val newShape = engine.createShape(solidGeometry)
                        body.shape.destroy()
                        body.shape = newShape
                    }
                }
                physics.broadQuery.overlapAABox(
                    box = AABB((pos * 16).toVec3d(), ((pos + 1) * 16).toVec3d()),
                    filter = movingFilter,
                ).forEach { access ->
                    access.writeOf<PhysicsBody.MovingWrite> { body ->
                        body.activate()
                    }
                }
            }
                 */
            }
        }
    }
}
