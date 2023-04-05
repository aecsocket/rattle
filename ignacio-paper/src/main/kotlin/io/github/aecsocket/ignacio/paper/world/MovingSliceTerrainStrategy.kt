package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.alexandria.Mutexed
import io.github.aecsocket.ignacio.*
import io.github.aecsocket.ignacio.paper.Ignacio
import io.github.aecsocket.ignacio.paper.asKlam
import io.github.aecsocket.klam.*
import kotlinx.coroutines.runBlocking
import org.bukkit.Chunk
import org.bukkit.ChunkSnapshot
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.util.Vector

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
) : TerrainStrategy {
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
    private val stepListener = StepListener {
        println("== STEP START")
        runBlocking { onPhysicsStep() }
        println("== STEP STOP")
    }
    private val contactFilter = engine.contactFilter(engine.layers.terrain)

    private val cubeCache = Mutexed(HashMap<FVec3, Shape>())
    private val blockShape: Shape
    private val shapeCache = Mutexed(HashMap<BlockData, Shape?>())
    private val slices = Mutexed(Slices())

    var enabled = true
        private set

    init {
        physics.onStep(stepListener)
        blockShape = runBlocking { cubeShape(FVec3(0.5f)) }
    }

    private suspend fun cubeShape(halfExtents: FVec3): Shape {
        return cubeCache.withLock { cubeCache ->
            cubeCache.computeIfAbsent(halfExtents) {
                engine.shape(BoxGeometry(halfExtents))
            }
        }
    }

    override fun destroy() {
        destroyed.mark()
        physics.removeStepListener(stepListener)

        runBlocking {
            shapeCache.withLock { shapeCache ->
                shapeCache.forEach { (_, shape) ->
                    shape?.destroy()
                }
                shapeCache.clear()
            }
            cubeCache.withLock { cubeCache ->
                cubeCache.forEach { (_, shape) ->
                    shape.destroy()
                }
                cubeCache.clear()
            }
        }
    }

    override fun enable() {
        enabled = true
    }

    override fun disable() {
        enabled = false
    }

    private data class SlicePositionUpdates(
        val toRemove: Set<IVec3>,
        val toSnapshot: Map<IVec2, Set<Int>>,
    )

    private fun println(msg: String) {
        kotlin.io.println("[${Thread.currentThread().name}] $msg")
    }

    private suspend fun onPhysicsStep() {
        // context: physics step; all bodies locked, cannot add or remove bodies
        if (!enabled) return

        // make sets of which slice positions we need to snapshot, and which to remove
        val (toRemove, toSnapshot) = computeSlicePositionUpdates()

        engine.launchTask {
            // context: physics non-step; can add or remove bodies
            // remove bodies for slices which we've marked as not needed
            println("! removing")
            removeSlices(toRemove)
            println("! removing DONE")
        }

        toSnapshot.forEach { (xz, sys) ->
            ignacio.scheduling.onChunk(world, xz.x, xz.y).launch {
                // context: chunk tick thread
                println("! computing snapshots")
                val toCreate = computeSliceSnapshots(xz.x, xz.y, sys)
                println("! computing snapshots DONE")
                engine.launchTask {
                    // context: physics non-step
                    println("! adding slices")
                    // TODO this blocks
                    addSlices(toCreate)
                    println("! adding slices DONE")
                }
            }
        }
    }

    private suspend fun computeSlicePositionUpdates(): SlicePositionUpdates {
        // we want to make bodies for all slices which are intersected by a body
        // and remove bodies for all slices which aren't intersected by any body
        val toRemove = slices.withLock { (slices) -> slices.keys.toMutableSet() }
        // key by chunk x,z for easy task scheduling later
        val toSnapshot = HashMap<IVec2, MutableSet<Int>>()
        physics.bodies.active().forEach { bodyId ->
            bodyId.readUnlocked { body ->
                // only create slices for moving objects
                // TODO custom layer support: make this variable
                if (body.contactFilter.layer != engine.layers.moving) return@readUnlocked

                // TODO expand/shrink this bound by velocity and constant factor
                val overlappingSlices = enclosedPoints(body.bounds / 16.0).toSet()
                toRemove -= overlappingSlices
                overlappingSlices.forEach { (sx, sy, sz) ->
                    toSnapshot.computeIfAbsent(IVec2(sx, sz)) { HashSet() } += sy
                }
            }
        }
        return SlicePositionUpdates(toRemove, toSnapshot)
    }

    private suspend fun removeSlices(slicePositions: Collection<IVec3>) {
        val bodies = slices.withLock { (slices) ->
            slicePositions.flatMap { slices.remove(it)?.bodies() ?: emptyList() }
        }
        // bulk-remove bodies to make it more efficient
        physics.bodies.removeAll(bodies)
        physics.bodies.destroyAll(bodies)
    }

    private suspend fun computeSliceSnapshots(sx: Int, sz: Int, sys: Collection<Int>): List<Pair<IVec3, SliceSnapshot>> {
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

    private suspend fun blockShape(block: Block): Shape? {
        return shapeCache.withLock { shapeCache ->
            // cache by block data instead of VoxelShapes, because it might be faster? idk
            val blockData = block.blockData
            // can't use computeIfAbsent because critical suspend point or something
            shapeCache[blockData]?.let { return@withLock it }
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

    private suspend fun addSlices(toCreate: List<Pair<IVec3, SliceSnapshot>>) {
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

            println(">> enter slice lock")
            slices.withLock { slices ->
                println(">> >> A")
                val layerBodies = layers.map { (layer, children) ->
                    println(">> >> A1")
                    val shape = engine.shape(StaticCompoundGeometry(children))
                    println(">> >> A2")
                    // this `createStatic` blocks, because it waits for mBodiesMutex to unlock
                    // but WTF locks it and doesn't unlock it??? mutex deadlock??
                    layer to physics.bodies.createStatic(StaticBodyDescriptor(
                        shape = shape,
                        contactFilter = contactFilter,
                        trigger = !layer.collidable,
                    ), Transform(DVec3(pos) * 16.0))
                }.associate { it }
                println(">> >>B")
                val slice = Slice(layerBodies)
                println(">> >> C")
                slices.slices[pos] = slice
                println(">> >> D")
                layerBodies.forEach { (layer, body) ->
                    slices.bodyToSlice[body] = slice to layer
                }
                println(">> >> E")
                layerBodies.values
            }.also {
                println(">> exit slice lock")
            }
        }.flatten()
        println(">> enter add bodies")
        physics.bodies.addAll(bodies)
        println(">> exit add bodies")
    }

    override fun physicsUpdate(deltaTime: Float) {}

    override fun syncUpdate() {}

    override fun isTerrain(body: PhysicsBody.Read) = slices.leak().bodyToSlice.containsKey(body.key)

    override fun onChunksLoad(chunks: Collection<Chunk>) {}

    override fun onChunksUnload(chunks: Collection<Chunk>) {}
}
