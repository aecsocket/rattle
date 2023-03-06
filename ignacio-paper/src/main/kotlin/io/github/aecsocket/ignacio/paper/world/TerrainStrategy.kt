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
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.data.BlockData
import java.util.concurrent.ConcurrentHashMap

sealed interface TerrainData {
    object Solid : TerrainData

    data class Fluid(val a: Int) : TerrainData
}

interface TerrainStrategy : Destroyable {
    fun terrainData(body: BodyRef): TerrainData?

    fun loadChunks(chunks: Collection<Chunk>)

    fun unloadChunks(chunks: Collection<Chunk>)
}

class NoOpTerrainStrategy : TerrainStrategy {
    override fun destroy() {}

    override fun loadChunks(chunks: Collection<Chunk>) {}

    override fun unloadChunks(chunks: Collection<Chunk>) {}

    override fun terrainData(body: BodyRef) = null
}

abstract class SliceTerrainStrategy(
    protected val ignacio: Ignacio,
    world: World,
    protected val physics: PhysicsSpace,
) : TerrainStrategy {
    data class SliceData(
        val slice: ChunkSlice,
        val solid: Solid?,
        val water: Water?,
    ) {
        data class Solid(
            val body: BodyRef,
            val data: TerrainData.Solid,
        )

        data class Water(
            val body: BodyRef,
            val data: TerrainData.Fluid,
        )

        fun terrainDataOf(body: BodyRef) = when (body) {
            solid?.body -> solid.data
            water?.body -> water.data
            else -> throw IllegalStateException("Body is not part of this terrain slice")
        }
    }

    private val destroyed = DestroyFlag()
    private val cube = ignacio.engine.createGeometry(BoxGeometrySettings(Vec3f(0.5f)))
    protected val startY = world.minHeight
    protected val numYSlices = (world.maxHeight - startY) / 16
    protected val sliceToData = ConcurrentHashMap<ChunkSlice, SliceData>()
    protected val bodyToData = ConcurrentHashMap<BodyRef, SliceData>()

    @OptIn(ExperimentalStdlibApi::class)
    protected fun ySlices() = (0 ..< numYSlices)

    override fun destroy() {
        destroyed.mark()
        physics.bodies.removeAll(this.bodyToData.keys)
        this.bodyToData.forEach { (body) ->
            physics.bodies.destroy(body)
        }
        sliceToData.clear()
        this.bodyToData.clear()
        cube.destroy()
    }

    override fun terrainData(body: BodyRef) = bodyToData[body]?.terrainDataOf(body)

    protected interface BlockBodySettings {
        data class Solid(
            val geometry: Geometry
        ) : BlockBodySettings

        data class Liquid(
            val geometry: Geometry
        ) : BlockBodySettings

        object Empty : BlockBodySettings
    }

    private fun getBlockSettings(block: BlockData): BlockBodySettings {
        val material = block.material
        return when {
            material == Material.WATER -> BlockBodySettings.Liquid(cube)
            material.isCollidable -> BlockBodySettings.Solid(cube)
            else -> BlockBodySettings.Empty
        }
    }

    data class ChunkData(
        val x: Int,
        val z: Int,
        val snapshot: ChunkSnapshot,
    )

    fun createSliceData(chunk: ChunkData, sy: Int): SliceData {
        val (sx, sz, snapshot) = chunk
        val sliceBase = Vec3d(sx * 16.0, startY + sy * 16.0, sz * 16.0)
        val solidChildren = ArrayList<CompoundChild>()
        val waterChildren = ArrayList<CompoundChild>()

        fun process(lx: Int, ly: Int, lz: Int) {
            val gy = startY + sy * 16 + ly
            // snapshot is of size 16x[world height]x16
            // we use the local block X, global block Y, local block Z
            val block = snapshot.getBlockData(lx, gy, lz)

            fun centerVec() = Vec3f(lx + 0.5f, ly + 0.5f, lz + 0.5f)

            when (val blockSettings = getBlockSettings(block)) {
                is BlockBodySettings.Solid -> {
                    solidChildren += CompoundChild(centerVec(), Quat.Identity, blockSettings.geometry)
                }
                is BlockBodySettings.Liquid -> {
                    waterChildren += CompoundChild(centerVec(), Quat.Identity, blockSettings.geometry)
                }
                is BlockBodySettings.Empty -> {}
            }
        }

        repeat(16) { lx ->
            repeat(16) { ly ->
                repeat(16) { lz ->
                    process(lx, ly, lz)
                }
            }
        }

        fun geometryOf(children: List<CompoundChild>) =
            ignacio.engine.createGeometry(StaticCompoundGeometrySettings(children))

        return SliceData(
            slice = ChunkSlice(sx, sy, sz),
            solid = if (solidChildren.isEmpty()) null else {
                SliceData.Solid(
                    body = physics.bodies.createStatic(StaticBodySettings(
                        geometry = geometryOf(solidChildren),
                        layer = ignacio.engine.layers.ofObject.terrain,
                    ), Transform(sliceBase)).ref,
                    data = TerrainData.Solid,
                )
            },
            water = if (waterChildren.isEmpty()) null else {
                SliceData.Water(
                    body = physics.bodies.createStatic(StaticBodySettings(
                        geometry = geometryOf(waterChildren),
                        layer = ignacio.engine.layers.ofObject.terrain,
                        isSensor = true,
                    ), Transform(sliceBase)).ref,
                    data = TerrainData.Fluid(1),
                )
            },
        )
    }

    protected fun Iterable<SliceData>.bodies() = flatMap { slice ->
        listOfNotNull(slice.solid?.body, slice.water?.body)
    }

    override fun unloadChunks(chunks: Collection<Chunk>) {
        val slices = chunks.flatMap { chunk ->
            ySlices().map { sy -> ChunkSlice(chunk.x, sy, chunk.z) }
        }

        ignacio.engine.runTask {
            val sliceData = slices.mapNotNull { slice -> sliceToData[slice] }
            val bodies = sliceData.bodies()
            physics.bodies {
                removeAll(bodies)
                destroyAll(bodies)
            }
        }
    }
}

class OnLoadTerrainStrategy(
    ignacio: Ignacio,
    world: World,
    physics: PhysicsSpace
) : SliceTerrainStrategy(ignacio, world, physics) {
    private fun createSlicesData(chunk: ChunkData): List<SliceData> {
        return ySlices().map { sy ->
            createSliceData(chunk, sy)
        }
    }

    override fun loadChunks(chunks: Collection<Chunk>) {
        val chunkData = chunks.map { chunk ->
            ChunkData(
                chunk.x,
                chunk.z,
                chunk.getChunkSnapshot(false, false, false)
            )
        }

        ignacio.engine.runTask {
            val sliceData = chunkData.map { chunk -> async { createSlicesData(chunk) } }.awaitAll().flatten()
            val bodies = ArrayList<BodyRef>()
            sliceData.forEach { data ->
                sliceToData[data.slice] = data
                data.solid?.body?.let {
                    bodies += it
                    bodyToData[it] = data
                }
                data.water?.body?.let {
                    bodies += it
                    bodyToData[it] = data
                }
            }
            physics.bodies.addAll(bodies, false)
        }
    }
}

class ByActiveTerrainStrategy(
    ignacio: Ignacio,
    world: World,
    physics: PhysicsSpace,
) : SliceTerrainStrategy(ignacio, world, physics) {
    private val stepListener = StepListener { deltaTime ->
        // TODO
    }

    init {
        physics.onStep(stepListener)
    }

    override fun destroy() {
        super.destroy()
        physics.removeStepListener(stepListener)
    }

    override fun loadChunks(chunks: Collection<Chunk>) {}
}
