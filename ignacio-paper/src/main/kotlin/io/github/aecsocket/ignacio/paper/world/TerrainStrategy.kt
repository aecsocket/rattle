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
import java.util.concurrent.ConcurrentHashMap
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

abstract class SliceTerrainStrategy(
    protected val ignacio: Ignacio,
    world: World,
    protected val physics: PhysicsSpace,
) : TerrainStrategy {
    private val destroyed = DestroyFlag()
    private val cube = ignacio.engine.createGeometry(BoxGeometrySettings(Vec3f(0.5f)))
    protected val startY = world.minHeight
    protected val numYSlices = (world.maxHeight - startY) / 16
    protected val sliceToBody = ConcurrentHashMap<ChunkSlice, BodyAccess>()
    protected val bodyToSlice = ConcurrentHashMap<BodyAccess, ChunkSlice>()

    @OptIn(ExperimentalStdlibApi::class)
    protected fun ySlices() = (0 ..< numYSlices)

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

    protected fun getBlockGeometry(block: BlockData): Geometry? {
        return if (block.material.isCollidable) {
            cube
        } else null
    }

    data class ChunkData(
        val x: Int,
        val z: Int,
        val snapshot: ChunkSnapshot,
    )

    fun createSliceBody(chunk: ChunkData, sy: Int): BodyAccess? {
        val (sx, sz, snapshot) = chunk
        val sliceBase = Vec3d(sx * 16.0, startY + sy * 16.0, sz * 16.0)
        val sliceChildren = ArrayList<CompoundChild>()

        fun process(lx: Int, ly: Int, lz: Int) {
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

        repeat(16) { lx ->
            repeat(16) { ly ->
                repeat(16) { lz ->
                    process(lx, ly, lz)
                }
            }
        }

        if (sliceChildren.isNotEmpty()) {
            val geometry = ignacio.engine.createGeometry(StaticCompoundGeometrySettings(
                children = sliceChildren
            ))
            return physics.bodies.createStatic(StaticBodySettings(
                geometry = geometry,
                layer = ignacio.engine.layers.ofObject.terrain,
            ), Transform(sliceBase))
        }
        return null
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

class OnLoadTerrainStrategy(
    ignacio: Ignacio,
    world: World,
    physics: PhysicsSpace
) : SliceTerrainStrategy(ignacio, world, physics) {
    private suspend fun createChunkBodies(chunk: ChunkData): List<Pair<ChunkSlice, BodyAccess>> = with(CoroutineScope(coroutineContext)) {
        val (sx, sz) = chunk
        return ySlices().map { sy ->
            val slice = ChunkSlice(sx, sy, sz)
            async {
                val body = createSliceBody(chunk, sy)
                body?.let { slice to it }
            }
        }.awaitAll().filterNotNull()
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
            physics.bodies.addAll(bodies.map { (_, body) -> body }, false)
            bodies.forEach { (slice, body) ->
                bodyToSlice[body] = slice
                sliceToBody[slice] = body
            }
        }
    }
}

class ByActiveTerrainStrategy(
    ignacio: Ignacio,
    world: World,
    physics: PhysicsSpace,
) : SliceTerrainStrategy(ignacio, world, physics) {
    private val stepListener: StepListener

    init {
        stepListener = physics.onStep { deltaTime ->
            physics.bodies.active()
        }
    }

    override fun destroy() {
        super.destroy()
        physics.removeOnStep(stepListener)
    }

    override fun loadChunks(chunks: Collection<Chunk>) {}
}
