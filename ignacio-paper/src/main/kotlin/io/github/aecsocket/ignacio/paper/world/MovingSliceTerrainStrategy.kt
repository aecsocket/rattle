package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.alexandria.Synchronized
import io.github.aecsocket.ignacio.*
import io.github.aecsocket.ignacio.paper.Ignacio
import io.github.aecsocket.ignacio.paper.asKlam
import io.github.aecsocket.klam.*
import org.bukkit.Chunk
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.util.Vector
import org.spongepowered.configurate.objectmapping.ConfigSerializable

sealed interface TerrainLayer {
    val collidable: Boolean

    object Solid : TerrainLayer {
        override val collidable get() = true
    }

    data class Fluid(
        val density: Float,
    ) : TerrainLayer {
        override val collidable get() = false
    }
}

val solidLayer = TerrainLayer.Solid
val waterLayer = TerrainLayer.Fluid(997.0f)
val lavaLayer = TerrainLayer.Fluid(3100.0f)
val terrainLayers = arrayOf(solidLayer, waterLayer, lavaLayer)
val terrainLayerToIndex = terrainLayers.mapIndexed { i, v -> v to i }.associate { it }

private const val SLICE_SIZE = 16 * 16 * 16

private val centerOffset = FVec3(0.5f)

fun enclosedPoints(b: DAabb3): Iterable<IVec3> {
    fun floor(s: Double): Int {
        val i = s.toInt()
        return if (s < i) i - 1 else i
    }

    fun ceil(s: Double): Int {
        val i = s.toInt()
        return if (s > i) i + 1 else i
    }

    val pMin = IVec3(floor(b.min.x), floor(b.min.y), floor(b.min.z))
    val pMax = IVec3(ceil(b.max.x), ceil(b.max.y), ceil(b.max.z))
    val extent = pMax - pMin
    val size = extent.x * extent.y * extent.z
    return object : Iterable<IVec3> {
        override fun iterator() = object : Iterator<IVec3> {
            var i = 0
            var dx = 0
            var dy = 0
            var dz = 0

            override fun hasNext() = i < size

            override fun next(): IVec3 {
                if (i >= size)
                    throw IndexOutOfBoundsException("($dx, $dy, $dz)")
                val point = pMin + IVec3(dx, dy, dz)
                dx += 1
                if (dx >= extent.x) {
                    dx = 0
                    dy += 1
                    if (dy >= extent.y) {
                        dy = 0
                        dz += 1
                    }
                }
                i += 1
                return point
            }
        }
    }
}

private fun localIndexToVector(i: Int) = IVec3(
    (i / 16 / 16) % 16,
    (i / 16) % 16,
    i % 16
)

private fun localVectorToIndex(v: IVec3) = (v.x * 16 * 16) + (v.y * 16) + v.z

class MovingSliceTerrainStrategy(
    private val ignacio: Ignacio,
    private val world: World,
    private val physics: PhysicsSpace,
    private val settings: Settings,
) : TerrainStrategy {
    /*
    phys step:
     * calculate slice positions to REMOVE and to CREATE
     * schedule some tasks on the chunks
    phys pre-step / update:
     * remove all slice positions that we calculated before
     * go through all the snapshots that we created in the chunk task
     * for each one, create a shape THEN add a body with that shape to the engine
    chunk task:
     * creates a snapshot of the chunk in that moment -> shapes, layer
     * safely passes that data to the pre-step
     */

    @ConfigSerializable
    data class Settings(
        val boundsExpansionVelocityFactor: Double = 0.2,
        val boundsExpansionConstant: DVec3 = DVec3(4.0, 4.0, 4.0),
    )

    data class TileSnapshot(
        val layer: TerrainLayer,
        val shape: Shape,
    )

    @JvmInline
    value class TileSnapshots(val tiles: Array<TileSnapshot?>) {
        operator fun get(index: Int) = tiles[index]

        operator fun set(index: Int, value: TileSnapshot?) { tiles[index] = value }

        fun createCompoundChildren(): Array<out List<CompoundChild>> {
            val layers: Array<MutableList<CompoundChild>> = Array(terrainLayers.size) { ArrayList() }
            tiles.forEachIndexed { i, tile ->
                if (tile == null) return@forEachIndexed
                val lx = (i / 16 / 16) % 16
                val ly = (i / 16) % 16
                val lz = i % 16

                layers[terrainLayerToIndex[tile.layer]!!] += CompoundChild(
                    shape = tile.shape,
                    position = FVec3(lx + 0.5f, ly + 0.5f, lz + 0.5f),
                    rotation = Quat.identity(),
                )
            }
            return layers
        }
    }

    class SliceSnapshot(
        val tiles: TileSnapshots,
    )

    class Slice(
        val tiles: TileSnapshots,
        val layers: List<PhysicsBody?>,
    ) {
        fun bodies() = layers.filterNotNull()
    }

    private data class Slices(
        val slices: MutableMap<IVec3, Slice> = HashMap(),
        val bodyToSlice: MutableMap<PhysicsBody, Pair<Slice, Int>> = HashMap(),
    )

    private val engine = ignacio.engine
    private val destroyed = DestroyFlag()
    private val yStart = world.minHeight
    private val ySize = world.maxHeight - yStart
    private val numSlices = ySize / 16
    private val negativeYSlices = -yStart / 16
    private val stepListener = StepListener { onPhysicsStep() }
    private val contactFilter = engine.contactFilter(engine.layers.terrain)
    private val movingLayerFilter = engine.filters.anyLayer // TODO

    private val cubeCache = Synchronized(HashMap<FVec3, Shape>())
    private val blockShape: Shape
    private val shapeCache = Synchronized(HashMap<BlockData, Shape?>())
    private val slices = Synchronized(Slices())

    private val toRemove = Synchronized(HashSet<IVec3>())
    private val toCreate = Synchronized(HashMap<IVec3, SliceSnapshot>())
    private val toUpdate = Synchronized(HashSet<IVec3>())

    var enabled = true
        private set

    init {
        physics.onStep(stepListener)
        blockShape = cubeShape(FVec3(0.5f))
    }

    private fun cubeShape(halfExtents: FVec3): Shape {
        return cubeCache.synchronized { cubeCache ->
            cubeCache.computeIfAbsent(halfExtents) {
                engine.shape(BoxGeometry(halfExtents))
            }
        }
    }

    override fun destroy() {
        destroyed.mark()
        physics.removeStepListener(stepListener)
        shapeCache.synchronized { shapeCache ->
            shapeCache.forEach { (_, shape) ->
                shape?.destroy()
            }
            shapeCache.clear()
        }
        cubeCache.synchronized { cubeCache ->
            cubeCache.forEach { (_, shape) ->
                shape.destroy()
            }
            cubeCache.clear()
        }
    }

    override fun enable() {
        enabled = true
    }

    override fun disable() {
        enabled = false
    }

    private fun onPhysicsStep() {
        // context: physics step; all bodies read-only locked, cannot add or remove bodies
        if (!enabled) return

        // NOTE: this function will not run when there are no active bodies in the world
        // therefore, if you spawn a bunch of moving bodies, which create a bunch of static terrain bodies,
        // even if you remove the moving bodies, the terrain will NOT be destroyed (yet) since this function won't run
        // but it *will* be cleaned up later when this function runs again

        // we want to make bodies for all slices which are intersected by a body
        // and remove bodies for all slices which aren't intersected by any body
        val toRemove = slices.synchronized { (slices) -> slices.keys.toMutableSet() }
        val toSnapshot = HashMap<IVec2, MutableSet<Int>>()
        physics.bodies.active().forEach { bodyKey ->
            bodyKey.readUnlockedAs<PhysicsBody.MovingRead> { body ->
                // only create slices for moving objects
                // TODO custom layer support: make this variable
                if (body.contactFilter.layer != engine.layers.moving) return@readUnlockedAs

                // expand bounds so that more potential blocks are included in the calculation
                // this is a function of the body's velocity as well
                val rawBounds = body.bounds
                val velocityExpansion = DVec3(body.linearVelocity) * settings.boundsExpansionVelocityFactor
                val expandedBounds = DAabb3(
                    min(rawBounds.min, rawBounds.min + velocityExpansion),
                    max(rawBounds.max, rawBounds.max + velocityExpansion)
                )
                val bounds = expand(expandedBounds, settings.boundsExpansionConstant)

                val overlappingSlices = enclosedPoints(bounds / 16.0).toSet()
                toRemove -= overlappingSlices
                overlappingSlices.forEach { pos ->
                    toSnapshot.computeIfAbsent(pos.xz) { HashSet() } += pos.y
                }
            }
        }

        // removed later during pre-step
        this.toRemove.synchronized { it += toRemove }
        toSnapshot.forEach { (xz, sys) ->
            ignacio.scheduling.onChunk(world, xz.x, xz.y).launch {
                val toAdd = createSliceSnapshots(xz.x, xz.y, sys)
                // created later during pre-step
                this@MovingSliceTerrainStrategy.toCreate.synchronized { it += toAdd }
            }
        }
    }

    override fun onPhysicsUpdate(deltaTime: Float) {
        // context: physics pre-step; can add, remove, or write bodies
        if (!enabled) return

        // remove old slices
        val toRemove = toRemove.synchronized { toRemove ->
            toRemove.toSet().also { toRemove.clear() }
        }
        val bodies = slices.synchronized { (slices) ->
            toRemove.flatMap { slices.remove(it)?.bodies() ?: emptyList() }
        }
        // bulk-remove bodies to make it more efficient
        physics.bodies.removeAll(bodies)
        physics.bodies.destroyAll(bodies)

        // create new slices
        val toCreate = this.toCreate.synchronized { toCreate ->
            toCreate.toList().also { toCreate.clear() }
        }
        createSliceBodies(toCreate)

        // update the shapes of slices which are scheduled for an update (block change)
        val toUpdate = toUpdate.synchronized { toUpdate ->
            toUpdate.toSet().also { toUpdate.clear() }
        }
        toUpdate.forEach { pos ->
            val slice = slices.synchronized { it.slices[pos] } ?: return@forEach

            // update shape
            val layerChildren = slice.tiles.createCompoundChildren()
            terrainLayers.forEachIndexed { layerIdx, _ ->
                val children = layerChildren[layerIdx].ifEmpty { return@forEachIndexed }
                val shape = engine.shape(StaticCompoundGeometry(children))
                // TODO this will not create a new body if one doesn't exist for this layer
                // e.g. if a new water block is placed in a slice which didn't have one before,
                // this will do nothing (bad!)
                slice.layers[layerIdx]?.let { bodyKey ->
                    // we must lock *for writing*, so we can't do this in the onPhysicsStep (read-only)
                    bodyKey.write { body ->
                        body.shape.destroy()
                        body.shape = shape
                    }
                }
            }

            // activate nearby bodies
            val sliceMin = pos * 16
            physics.broadQuery.contactBox(
                DAabb3(DVec3(sliceMin), DVec3(sliceMin + 16)),
                movingLayerFilter,
            ).forEach { bodyKey ->
                bodyKey.writeAs<PhysicsBody.MovingWrite> { body ->
                    body.activate()
                }
            }
        }
    }

    private fun createSliceSnapshots(sx: Int, sz: Int, sys: Collection<Int>): List<Pair<IVec3, SliceSnapshot>> {
        if (!world.isChunkLoaded(sx, sz)) return emptyList()
        val chunk = world.getChunkAt(sx, sz)
        val snapshot = chunk.getChunkSnapshot(false, false, false)
        // build snapshots of the slices we want to create bodies for later
        val toCreate = ArrayList<Pair<IVec3, SliceSnapshot>>()
        sys.forEach { sy ->
            val pos = IVec3(sx, sy, sz)
            // we already have a body for this slice; don't process it
            if (slices.leak().slices.contains(pos)) return@forEach
            // iy is the index of the Y slice, as stored by server internals
            //     if sy: -4..16
            //   then iy: 0..20
            val iy = sy + negativeYSlices
            // out of range; can't read any blocks here
            if (iy < 0 || iy >= numSlices) return@forEach
            // empty slices don't need to be created
            if (snapshot.isSectionEmpty(iy)) return@forEach

            // chunk snapshots don't contain block shapes, so we have to cache these ourselves
            // so we can send them over to have collision shapes made out of them later
            // null = no shape (air/passable)
            val tiles: Array<TileSnapshot?> = Array(SLICE_SIZE) { i ->
                val (lx, ly, lz) = localIndexToVector(i)
                val gy = sy * 16 + ly
                tileSnapshot(chunk.getBlock(lx, gy, lz))
            }

            toCreate += pos to SliceSnapshot(
                tiles = TileSnapshots(tiles),
            )
        }
        return toCreate
    }

    private fun tileSnapshot(block: Block): TileSnapshot? {
        return when {
            block.isPassable -> null
            block.type == Material.WATER -> TileSnapshot(waterLayer, blockShape)
            block.type == Material.LAVA -> TileSnapshot(lavaLayer, blockShape)
            else -> blockShape(block)?.let { shape -> TileSnapshot(solidLayer, shape) }
        }
    }

    private fun blockShape(block: Block): Shape? {
        return shapeCache.synchronized { shapeCache ->
            // cache by block data instead of VoxelShapes, because it might be faster? idk
            val blockData = block.blockData
            // can't use computeIfAbsent because critical suspend point or something
            shapeCache[blockData]?.let { return@synchronized it }
            // but otherwise, just fall back generating the shape ourselves
            val boxes = block.collisionShape.boundingBoxes
            val shape = when {
                boxes.isEmpty() -> null
                boxes.size == 1 && boxes.first().center == Vector(0.5, 0.5, 0.5) -> {
                    val box = boxes.first()
                    val halfExtent = (box.max.asKlam() - box.min.asKlam()) / 2.0
                    cubeShape(FVec3(halfExtent))
                }
                else -> {
                    val children = boxes.map { box ->
                        val halfExtent = (box.max.asKlam() - box.min.asKlam()) / 2.0
                        CompoundChild(
                            shape = cubeShape(FVec3(halfExtent)),
                            position = FVec3(box.center.asKlam()) - centerOffset,
                            rotation = Quat.identity(),
                        )
                    }
                    engine.shape(StaticCompoundGeometry(children))
                }
            }
            shape.also { shapeCache[blockData] = it }
        }
    }

    private fun createSliceBodies(toCreate: List<Pair<IVec3, SliceSnapshot>>) {
        val bodies = toCreate.map { (pos, snapshot) ->
            val layerChildren = snapshot.tiles.createCompoundChildren()
            val layerBodies = terrainLayers.mapIndexed { layerIdx, layer ->
                val children = layerChildren[layerIdx].ifEmpty { return@mapIndexed null }

                val shape = engine.shape(StaticCompoundGeometry(children))
                physics.bodies.createStatic(StaticBodyDescriptor(
                    shape = shape,
                    contactFilter = contactFilter,
                    trigger = !layer.collidable
                ), Transform(DVec3(pos) * 16.0))
            }
            val slice = Slice(snapshot.tiles, layerBodies)

            slices.synchronized { slices ->
                slices.slices[pos] = slice
                layerBodies.forEachIndexed { layerIdx, body ->
                    body?.let { slices.bodyToSlice[it] = slice to layerIdx }
                }
                layerBodies.filterNotNull()
            }
        }.flatten()
        // bulk add bodies so that the broad-phase quadtree is more optimized
        physics.bodies.addAll(bodies)
    }

    override fun isTerrain(body: PhysicsBody.Read) = slices.leak().bodyToSlice.containsKey(body.key)

    override fun onChunksLoad(chunks: Collection<Chunk>) {}

    override fun onChunksUnload(chunks: Collection<Chunk>) {}

    override fun onBlockUpdate(update: BlockUpdate) {
        val slicePos = update.position / 16
        // update slice tile data
        val slice = slices.synchronized { it.slices[slicePos] } ?: return
        // TODO is Math.floorMod slow?
        val tileIdx = localVectorToIndex(update.position.map { Math.floorMod(it, 16) })
        // create a snapshot of the new tile, and mutate the slice with this new data
        slice.tiles[tileIdx] = when (update) {
            is BlockUpdate.Remove -> null
            is BlockUpdate.Set -> tileSnapshot(update.block)
        }
        // schedule an update to run for this slice pos later
        toUpdate.synchronized { it += slicePos }
    }
}
