package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.math.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.bukkit.Chunk
import org.bukkit.ChunkSnapshot
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.data.BlockData

typealias SlicePos = Point3

sealed interface TerrainLayer {
    object Solid : TerrainLayer

    data class Fluid(
        val density: Float, // kg/m^3
    ) : TerrainLayer
}

val solidLayer = TerrainLayer.Solid
val waterLayer = TerrainLayer.Fluid(density = 997.0f)
val lavaLayer = TerrainLayer.Fluid(density = 3100.0f)

abstract class SliceTerrainStrategy(
    protected val engine: IgnacioEngine,
    protected val world: World,
    protected val physics: PhysicsSpace,
) : TerrainStrategy {
    data class SliceData(
        val pos: SlicePos,
        val snapshot: ChunkSnapshot,
    )

    data class TileData(
        val layer: TerrainLayer,
        val shape: Shape,
    )

    data class LayerData(
        val layer: TerrainLayer,
        val slice: SlicePos,
        val body: BodyRef,
    )

    private val cube = engine.createShape(BoxGeometry(Vec3f(0.5f)))
    protected val startY = world.minHeight
    protected val numSlices = (world.maxHeight - startY) / 16
    protected val sliceToLayers = HashMap<SlicePos, MutableMap<TerrainLayer, LayerData>>()
    protected val bodyToLayer = HashMap<BodyRef, LayerData>()

    override fun destroy() {
        cube.destroy()
    }

    @OptIn(ExperimentalStdlibApi::class)
    protected fun ySlices() = (0 ..< numSlices)

    private fun tileDataOf(block: BlockData): TileData? {
        val material = block.material
        when (block.material) {
            Material.WATER -> return TileData(waterLayer, cube)
            Material.LAVA -> return TileData(lavaLayer, cube)
            else -> {}
        }

        when {
            material.isCollidable -> {
                // TODO: get shape from snapshot
                // val voxelShape: VoxelShape = (block as CraftBlockData).state.getCollisionShape((world as CraftWorld).handle, BlockPos(pos.x, pos.y, pos.z))
                val shape = cube
                return TileData(solidLayer, shape)
            }
            else -> return null
        }
    }

    fun createSlice(slice: SliceData): Collection<LayerData> {
        val (pos, snapshot) = slice
        val (sx, sy, sz) = pos
        val layers = HashMap<TerrainLayer, MutableCollection<CompoundChild>>()
        if (slice.snapshot.isSectionEmpty(sy))
            return emptyList()

        fun process(lx: Int, ly: Int, lz: Int) {
            val gy = startY + sy * 16 + ly
            val tileData = tileDataOf(snapshot.getBlockData(lx, gy, lz)) ?: return
            val forLayer = layers.computeIfAbsent(tileData.layer) { ArrayList() }
            forLayer += CompoundChild(
                shape = tileData.shape,
                position = Vec3f(lx + 0.5f, ly + 0.5f, lz + 0.5f),
                rotation = Quat.Identity,
            )
        }

        repeat(16) { lx ->
            repeat(16) { ly ->
                repeat(16) { lz ->
                    process(lx, ly, lz)
                }
            }
        }

        return layers.map { (layer, children) ->
            val shape = engine.createShape(StaticCompoundGeometry(children))
            LayerData(
                layer = layer,
                slice = slice.pos,
                body = physics.bodies.createStatic(
                    StaticBodySettings(
                        shape = shape,
                        layer = engine.layers.ofObject.terrain,
                        isSensor = when (layer) {
                            solidLayer -> false
                            else -> true
                        },
                    ),
                    Transform(Vec3d(sx * 16.0, startY + sy * 16.0, sz * 16.0))
                ).ref
            )
        }
    }

    fun destroySlices(slices: Collection<SlicePos>) {
        val bodies = slices.flatMap { slice ->
            sliceToLayers.remove(slice)?.values?.map { layer ->
                layer.body.also { bodyToLayer.remove(it) }
            } ?: emptyList()
        }
        physics.bodies {
            removeAll(bodies)
            destroyAll(bodies)
        }
    }

    fun destroySlice(slice: SlicePos) {
        destroySlices(setOf(slice))
    }

    override fun isTerrain(body: BodyRef) = bodyToLayer.contains(body)

    override fun onChunksUnload(chunks: Collection<Chunk>) {
        val slices = chunks.flatMap { chunk ->
            ySlices().map { sy -> SlicePos(chunk.x, sy, chunk.z) }
        }
        destroySlices(slices)
    }
}

class SliceLoadTerrainStrategy(
    engine: IgnacioEngine,
    world: World,
    physics: PhysicsSpace,
) : SliceTerrainStrategy(engine, world, physics) {
    override fun onChunksLoad(chunks: Collection<Chunk>) {
        val slices = chunks.flatMap { chunk ->
            ySlices().map { sy ->
                val slicePos = SlicePos(chunk.x, sy, chunk.z)
                if (sliceToLayers.contains(slicePos))
                    // regenerate slice
                    destroySlice(slicePos)

                SliceData(
                    pos = slicePos,
                    snapshot = chunk.getChunkSnapshot(false, false, false)
                )
            }
        }

        engine.launchTask {
            val layers = slices.map { slice ->
                async { createSlice(slice) }
            }.awaitAll().flatten()
            layers.forEach { layer ->
                sliceToLayers[layer.slice]
            }
            physics.bodies.addAll(layers.map { it.body }, false)
        }
    }


    override fun onBlocksUpdate(blocks: Collection<BlockPos>) {

    }
}
