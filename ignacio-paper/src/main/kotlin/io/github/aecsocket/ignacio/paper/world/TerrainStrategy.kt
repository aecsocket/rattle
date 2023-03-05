package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.math.Quat
import io.github.aecsocket.ignacio.core.math.Transform
import io.github.aecsocket.ignacio.core.math.Vec3d
import io.github.aecsocket.ignacio.core.math.Vec3f
import io.github.aecsocket.ignacio.paper.Ignacio
import kotlinx.coroutines.*
import org.bukkit.Chunk
import org.bukkit.ChunkSnapshot
import org.bukkit.World
import org.bukkit.block.data.BlockData
import kotlin.coroutines.coroutineContext

interface TerrainStrategy : Destroyable {
    fun isTerrain(body: BodyAccess): Boolean

    fun loadChunks(chunks: Collection<Chunk>)

    fun unloadChunks(chunks: Collection<Chunk>)
}

class NoOpTerrainStrategy : TerrainStrategy {
    override fun destroy() {}

    override fun loadChunks(chunks: Collection<Chunk>) {}

    override fun unloadChunks(chunks: Collection<Chunk>) {}

    override fun isTerrain(body: BodyAccess) = false
}

class PregenTerrainStrategy(
    private val ignacio: Ignacio,
    world: World,
    private val physics: PhysicsSpace,
) : TerrainStrategy {
    private val destroyed = DestroyFlag()
    private val startY = world.minHeight
    private val numYSlices = (world.maxHeight - startY) / 16
    private val cube = ignacio.engine.createGeometry(BoxGeometrySettings(Vec3f(0.5f)))
    private val sliceToBody = HashMap<ChunkSlice, BodyAccess>()
    private val bodyToSlice = HashMap<BodyAccess, ChunkSlice>()

    @OptIn(ExperimentalStdlibApi::class)
    private fun ySlices() = (0 ..< numYSlices)

    override fun destroy() {
        destroyed.mark()
        physics.bodies.removeAll(bodyToSlice.keys)
        bodyToSlice.forEach { (body) ->
            physics.bodies.destroy(body)
        }
        sliceToBody.clear()
        bodyToSlice.clear()
        cube.destroy()
    }

    override fun isTerrain(body: BodyAccess) = bodyToSlice.contains(body)

    private data class ChunkData(
        val x: Int,
        val z: Int,
        val snapshot: ChunkSnapshot,
    )

    private fun getBlockGeometry(block: BlockData): Geometry? {
        return if (block.material.isCollidable) {
            cube
        } else null
    }

    private suspend fun createChunkBodies(chunk: ChunkData): List<BodyAccess> = with(CoroutineScope(coroutineContext)) {
        val (sx, sz, snapshot) = chunk
        val chunkBase = Vec3d(sx * 16.0, 0.0, sz * 16.0)

        return ySlices().map { sy -> async {
            val slice = ChunkSlice(sx, sy, sz)
            val sliceBase = chunkBase.copy(y = startY + sy * 16.0)
            val sliceChildren = ArrayList<CompoundChild>()

            repeat(16) { lx ->
                repeat(16) { ly ->
                    repeat(16) { lz ->
                        val gy = startY + sy * 16 + ly
                        // snapshot is of size 16x[world height]x16
                        // we use the local block X, global block Y, local block Z
                        val block = snapshot.getBlockData(lx, gy, lz)
                        getBlockGeometry(block)?.let { geom ->
                            sliceChildren += CompoundChild(
                                Vec3f(lx + 0.5f, ly + 0.5f, lz + 0.5f),
                                Quat.Identity,
                                geom
                            )
                        }
                    }
                }
            }

            if (sliceChildren.isNotEmpty()) {
                val geometry = ignacio.engine.createGeometry(StaticCompoundGeometrySettings(
                    children = sliceChildren
                ))
                val body = physics.bodies.addStatic(StaticBodySettings(
                    geometry = geometry,
                    layer = ignacio.engine.layers.ofObject.terrain,
                ), Transform(sliceBase))

                sliceToBody[slice] = body
                bodyToSlice[body] = slice
                body
            } else null
        } }.awaitAll().filterNotNull()
    }

    override fun loadChunks(chunks: Collection<Chunk>) {
        val data = chunks.map { chunk ->
            ChunkData(
                chunk.x,
                chunk.z,
                chunk.getChunkSnapshot(false, false, false)
            )
        }

        ignacio.engine.runTask {
            val bodies = data.map { chunk -> async { createChunkBodies(chunk) } }.awaitAll().flatten()
            physics.bodies.addAll(bodies, false)
        }
    }

    override fun unloadChunks(chunks: Collection<Chunk>) {
        val slices = chunks.flatMap { chunk ->
            ySlices().map { sy -> ChunkSlice(chunk.x, sy, chunk.z) }
        }

        ignacio.engine.runTask {
            val bodies = slices.mapNotNull { slice -> sliceToBody[slice] }
            physics.bodies.removeAll(bodies)
            bodies.forEach { body ->
                physics.bodies.destroy(body)
            }
        }
    }
}
