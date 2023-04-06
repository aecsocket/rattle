package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.alexandria.Synchronized
import io.github.aecsocket.ignacio.*
import io.github.aecsocket.ignacio.paper.Ignacio
import io.github.aecsocket.ignacio.paper.asKlam
import io.github.aecsocket.klam.*
import org.bukkit.Chunk
import org.bukkit.ChunkSnapshot
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

class MovingSliceTerrainStrategy(
    private val ignacio: Ignacio,
    private val world: World,
    private val physics: PhysicsSpace,
    private val settings: Settings,
) : TerrainStrategy {
    @ConfigSerializable
    data class Settings(
        val boundsExpansionVelocityFactor: Double = 0.2,
        val boundsExpansionConstant: DVec3 = DVec3(4.0, 4.0, 4.0),
    )

    class SliceSnapshot(
        val blocks: ChunkSnapshot,
        val shapes: Array<Shape?>,
    )

    data class Slice(
        val layers: Map<TerrainLayer, PhysicsBody>,
    ) {
        fun bodies() = layers.map { (_, body) -> body }
    }

    private data class Slices(
        val slices: MutableMap<IVec3, Slice> = HashMap(),
        val bodyToSlice: MutableMap<PhysicsBody, Pair<Slice, TerrainLayer>> = HashMap(),
    )

    private val engine = ignacio.engine
    private val destroyed = DestroyFlag()
    private val yStart = world.minHeight
    private val ySize = world.maxHeight - yStart
    private val numSlices = ySize / 16
    private val negativeYSlices = -yStart / 16
    private val stepListener = StepListener { onPhysicsStep() }
    private val contactFilter = engine.contactFilter(engine.layers.terrain)

    private val cubeCache = Synchronized(HashMap<FVec3, Shape>())
    private val blockShape: Shape
    private val shapeCache = Synchronized(HashMap<BlockData, Shape?>())
    private val slices = Synchronized(Slices())

    private val toRemove = Synchronized(HashSet<IVec3>())
    private val toCreate = Synchronized(ArrayList<Pair<IVec3, SliceSnapshot>>())

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
        // context: physics step; all bodies locked, cannot add or remove bodies
        if (!enabled) return

        // NOTE: this function will not run when there are no active bodies in the world
        // therefore, if you spawn a bunch of moving bodies, which create a bunch of static terrain bodies,
        // even if you remove the moving bodies, the terrain will NOT be destroyed (yet) since this function won't run
        // but it *will* be cleaned up later when this function runs again

        // we want to make bodies for all slices which are intersected by a body
        // and remove bodies for all slices which aren't intersected by any body
        val toRemove = slices.synchronized { (slices) -> slices.keys.toMutableSet() }
        val toSnapshot = HashMap<IVec2, MutableSet<Int>>()
        physics.bodies.active().forEach { bodyId ->
            bodyId.readUnlockedAs<PhysicsBody.MovingRead> { body ->
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
        // context: physics pre-step; can add or remove bodies
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
            val shapes: Array<Shape?> = Array(SLICE_SIZE) { i ->
                val lx = (i / 16 / 16) % 16
                val ly = (i / 16) % 16
                val lz = i % 16
                val gy = sy * 16 + ly
                val block = chunk.getBlock(lx, gy, lz)
                when {
                    block.isPassable -> null
                    else -> blockShape(block)
                }
            }

            toCreate += pos to SliceSnapshot(
                blocks = snapshot,
                shapes = shapes,
            )
        }
        return toCreate
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
            val sy = pos.y
            val layers = HashMap<TerrainLayer, MutableList<CompoundChild>>()

            fun add(layer: TerrainLayer, child: CompoundChild) {
                layers.computeIfAbsent(layer) { ArrayList() } += child
            }

            (0 until SLICE_SIZE).forEach { i ->
                val lx = (i / 16 / 16) % 16
                val ly = (i / 16) % 16
                val lz = i % 16
                val gy = sy * 16 + ly
                val block = snapshot.blocks.getBlockData(lx, gy, lz)
                when (block.material) {
                    Material.WATER -> add(waterLayer, CompoundChild(
                        shape = blockShape,
                        position = FVec3(lx + 0.5f, ly + 0.5f, lz + 0.5f),
                        rotation = Quat.identity(),
                    ))
                    Material.LAVA -> add(lavaLayer, CompoundChild(
                        shape = blockShape,
                        position = FVec3(lx + 0.5f, ly + 0.5f, lz + 0.5f),
                        rotation = Quat.identity(),
                    ))
                    else -> {
                        val shape = snapshot.shapes[i] ?: return@forEach
                        add(solidLayer, CompoundChild(
                            shape = shape,
                            position = FVec3(lx + 0.5f, ly + 0.5f, lz + 0.5f),
                            rotation = Quat.identity(),
                        ))
                    }
                }
            }

            slices.synchronized { slices ->
                val layerBodies = layers.map { (layer, children) ->
                    val shape = engine.shape(StaticCompoundGeometry(children))
                    layer to physics.bodies.createStatic(StaticBodyDescriptor(
                        shape = shape,
                        contactFilter = contactFilter,
                        trigger = !layer.collidable,
                    ), Transform(DVec3(pos) * 16.0))
                }.associate { it }
                val slice = Slice(layerBodies)

                slices.slices[pos] = slice
                layerBodies.forEach { (layer, body) ->
                    slices.bodyToSlice[body] = slice to layer
                }
                layerBodies.values
            }
        }.flatten()
        // bulk add bodies so that the broad-phase quadtree is more optimized
        physics.bodies.addAll(bodies)
    }

    override fun isTerrain(body: PhysicsBody.Read) = slices.leak().bodyToSlice.containsKey(body.key)

    override fun onChunksLoad(chunks: Collection<Chunk>) {}

    override fun onChunksUnload(chunks: Collection<Chunk>) {}
}
