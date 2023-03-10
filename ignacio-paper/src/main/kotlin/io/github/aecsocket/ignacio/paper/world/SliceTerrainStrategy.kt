package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.math.*
import io.github.aecsocket.ignacio.paper.ignacioBodyName
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.bukkit.Chunk
import org.bukkit.ChunkSnapshot
import org.bukkit.World
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

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

    private val toRemove: MutableSet<SlicePos> = Collections.newSetFromMap(ConcurrentHashMap())
    private val sliceSnapshots: MutableMap<SlicePos, SliceSnapshot> = ConcurrentHashMap()
    private val toCreate: MutableSet<SlicePos> = HashSet()
    private val toUpdate: MutableSet<SlicePos> = HashSet()

    private val sliceData = ConcurrentHashMap<SlicePos, SliceData>()
    private val bodyToSlice = ConcurrentHashMap<PhysicsBody, SliceData>()

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
        val chunkSnapshots = HashMap<Long, ChunkSnapshot>()
        fun createFor(pos: SlicePos) {
            // note that values from toCreate and toUpdate may linger between ticks, or be duplicated in both
            // therefore we make sure we haven't made a snapshot for this slice yet
            if (sliceSnapshots.contains(pos)) return
            if (!isValidPosition(pos)) return
            if (sliceData.contains(pos)) return

            val chunkKey = Chunk.getChunkKey(pos.x, pos.z)
            val blocks = chunkSnapshots.computeIfAbsent(chunkKey) {
                world.getChunkAt(pos.x, pos.z).getChunkSnapshot(false, false, false)
            }
            if (blocks.isSectionEmpty(pos.y + startYSlices)) return

            sliceSnapshots[pos] = SliceSnapshot(
                pos = pos,
                blocks = blocks,
            )
        }
        toCreate.forEach { createFor(it) }
        toUpdate.forEach { createFor(it) }

        // this task happens while bodies are still locked, not during any update
        // unlike physicsUpdate, which happens during body lock
        engine.launchTask {
            // clear all the bodies we've marked as unused last update
            val bodiesToRemove = toRemove.mapNotNull { pos ->
                sliceData.remove(pos)?.body?.also {
                    bodyToSlice -= it
                }
            }
            toRemove.clear()
            physics.bodies {
                removeAll(bodiesToRemove)
                destroyAll(bodiesToRemove)
            }

            // create all the bodies we've created snapshots for
            launch {
                val createSlices = toCreate.mapNotNull { pos ->
                    sliceSnapshots[pos]?.let { async { createSliceData(it) } }
                }.awaitAll()

                val bodiesToAdd = ArrayList<PhysicsBody>()
                createSlices.forEach { data ->
                    sliceData[data.pos] = data
                    data.body?.let {
                        bodyToSlice[it] = data
                        bodiesToAdd += it
                    }
                }
                physics.bodies.addAll(bodiesToAdd, false)
            }

            // update all the bodies' shapes we've created snapshots for
            launch {
                toUpdate.forEach { pos ->
                    val snapshot = sliceSnapshots[pos] ?: return@forEach
                    val data = sliceData[pos] ?: return@forEach
                    val solidGeometry = createGeometry(snapshot)
                    data.body?.let { access ->
                        solidGeometry?.let {
                            access.write { body ->
                                body.shape.destroy()
                                body.shape = engine.createShape(solidGeometry)
                            }
                        } ?: physics.bodies {
                            remove(access)
                            destroy(access)
                        }
                    }
                }
            }
        }
    }

    override fun physicsUpdate(deltaTime: Float) {
        if (!enabled) return

        toRemove.clear()
        toRemove += HashSet(sliceData.keys)
        toCreate.clear()
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

                nearbyBodies.forEach { access ->
                    // todo potentially STUPIDLY UNSAFE!!!
                    access.writeUnlockedOf<PhysicsBody.MovingWrite> { body ->
                        body.activate()
                    }
                }
                toUpdate += pos
            }
        }
    }
}
