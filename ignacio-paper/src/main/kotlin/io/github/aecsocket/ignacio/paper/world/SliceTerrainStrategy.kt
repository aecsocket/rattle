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

    private val toRemove = HashSet<SlicePos>()
    private val toCreate = HashSet<SlicePos>()
    private val toSnapshot = HashMap<SlicePos, SliceSnapshot>()
    private val chunkSnapshots = HashMap<Long, ChunkSnapshot>()

    private val sliceData = HashMap<SlicePos, SliceData>()
    private val bodyToSlice = HashMap<PhysicsBody, SliceData>()

    override fun destroy() {
        cube.destroy()
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
                name = "$bodyKey $slice",
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
        // create snapshots for positions
        synchronized(toCreate) {
            synchronized(toSnapshot) {
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
                toCreate.clear()
                chunkSnapshots.clear()
            }
        }

        engine.launchTask {
            // clear all the bodies we've marked as unused last tick
            synchronized(toRemove) {
                val bodiesToRemove = synchronized(sliceData) {
                    synchronized(bodyToSlice) {
                        toRemove.mapNotNull { pos ->
                            sliceData.remove(pos)?.body?.also {
                                bodyToSlice -= it
                            }
                        }
                    }
                }
                physics.bodies {
                    removeAll(bodiesToRemove)
                    destroyAll(bodiesToRemove)
                }
                toRemove.clear()
            }

            // create all the bodies we've created snapshots for last tick
            val slices = synchronized(toSnapshot) {
                toSnapshot.map { (_, slice) ->
                    async { createSliceData(slice) }
                }
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
            toSnapshot.clear()
        }
    }

    override fun physicsUpdate(deltaTime: Float) {
        synchronized(toCreate) {
            // mark all the chunk slices we need to create collision for
            // and any existing slices which we don't need for collision for, we mark toRemove
            toRemove += synchronized(sliceData) { sliceData.keys }
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
    }

    override fun isTerrain(body: PhysicsBody) = synchronized(bodyToSlice) { bodyToSlice.contains(body) }

    override fun onChunksLoad(chunks: Collection<Chunk>) {}

    override fun onChunksUnload(chunks: Collection<Chunk>) {

    }

    override fun onBlocksUpdate(blocks: Collection<BlockPos>) {

    }
}
