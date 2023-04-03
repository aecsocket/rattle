package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.ignacio.*
import io.github.aecsocket.ignacio.paper.Ignacio
import io.github.aecsocket.ignacio.paper.asKlam
import io.github.aecsocket.klam.*
import org.bukkit.Chunk
import org.bukkit.ChunkSnapshot
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockState
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

class SliceTerrainStrategy(
    private val ignacio: Ignacio,
    private val world: World,
    private val physics: PhysicsSpace,
) : TerrainStrategy {
    class SliceSnapshot(
        val pos: IVec3,
        val blocks: ChunkSnapshot,
        val blockShapes: Array<Shape?>,
    )

    data class Slice(
        val layers: Map<TerrainLayer, PhysicsBody>,
    )

    private val engine = ignacio.engine
    private val destroyed = DestroyFlag()
    private val cube = engine.shape(BoxGeometry(FVec3(0.5f)))
    private val yStart = world.minHeight
    private val ySize = world.maxHeight - yStart
    private val numSlices = ySize / 16
    private val negativeYSlices = -yStart / 16

    /*
    The key to good performance + safety here, is to lock fields enough to be safe, but hold them for short periods
    to not starve our other threads

    physics step - all bodies unlocked, cannot add/remove bodies:
      * collect all slice positions that we want to snapshot and create later (read all active bodies) into `toSnapshot`
      * compute slice positions that we no longer need, into `toRemove`
    physics update - all bodies locked, can add/remove bodies:
      * remove all bodies in `toRemove` and clear it
    processing region (set of chunks):
      * for all `toSnapshot` positions that are in this region...
        * create a snapshot and put it into `toCreate`
     */

    private val toSnapshot: MutableMap<Long, MutableSet<Int>> = HashMap()
    private var toRemove: MutableSet<IVec3> = HashSet()
    private val toCreate: MutableMap<IVec3, SliceSnapshot> = HashMap()

    private val cubeCache = HashMap<FVec3, Shape>()
    private val shapeCache = HashMap<BlockData, Shape?>()
    private val slices = HashMap<IVec3, Slice>()

    var enabled = true
        private set

    override fun destroy() {
        destroyed.mark()
        synchronized(shapeCache) {
            shapeCache.forEach { (_, shape) ->
                shape?.destroy()
            }
            shapeCache.clear()
        }
        synchronized(cubeCache) {
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

    override fun physicsUpdate(deltaTime: Float) {
        if (!enabled) return

        // make sets of which slice positions we need to snapshot, and which to remove
        // we want to make bodies for all slices which are intersected by a body
        val toRemove = synchronized(slices) { slices.keys.toMutableSet() }
        val toSnapshot = HashSet<IVec3>()
        physics.bodies.active().forEach { bodyId ->
            bodyId.readUnlocked { body ->
                // only create slices for moving objects
                // TODO custom layer support: make this variable
                if (body.contactFilter.layer != engine.layers.moving) return@readUnlocked
                val overlappingSlices = enclosedPoints(body.bounds / 16.0).toSet()
                toSnapshot += overlappingSlices
                toRemove -= overlappingSlices
            }
        }

        engine.launchTask {
            // TODO toRemove
        }

        ignacio.scheduling.onChunk(world)

        synchronized(this.toSnapshot) {
            // key by chunk keys themselves, to speed up the 2nd stage
            toSnapshot.forEach { pos ->
                this.toSnapshot.computeIfAbsent(Chunk.getChunkKey(pos.x, pos.z)) { HashSet() } += pos.y
            }
        }
    }

    private fun cubeShape(halfExtents: FVec3): Shape {
        return synchronized(cubeCache) {
            cubeCache.computeIfAbsent(halfExtents) {
                engine.shape(BoxGeometry(halfExtents))
            }
        }
    }

    private fun blockShape(block: Block): Shape? {
        // cache by block data instead of VoxelShapes, because it might be faster? idk
        return synchronized(shapeCache) {
            shapeCache.computeIfAbsent(block.blockData) {
                // but otherwise, just fall back generating the shape ourselves
                val boxes = block.collisionShape.boundingBoxes
                when {
                    boxes.isEmpty() -> null
                    boxes.size == 1 && boxes.first().center == Vector(0.0, 0.0, 0.0) -> {
                        val box = boxes.first()
                        val halfExtent = (box.max.asKlam() - box.min.asKlam()) / 2.0
                        cubeShape(FVec3(halfExtent))
                    }

                    else -> {
                        val children = boxes.map { box ->
                            val halfExtent = (box.max.asKlam() - box.min.asKlam()) / 2.0
                            CompoundChild(
                                shape = cubeShape(FVec3(halfExtent)),
                                position = FVec3(box.center.asKlam()),
                                rotation = Quat.identity(),
                            )
                        }
                        engine.shape(StaticCompoundGeometry(children))
                    }
                }
            }
        }
    }

    private fun chunkUpdate(chunk: Chunk) {
        if (!chunk.isLoaded)
            throw IllegalStateException("Updating chunk is not loaded")
        val sx = chunk.x
        val sz = chunk.z
        val chunkKey = Chunk.getChunkKey(sx, sz)
        // consume the slices we want to process, or early exit
        val toSnapshot = synchronized(toSnapshot) { toSnapshot.remove(chunkKey)?.toSet() } ?: return

        val snapshot by lazy { chunk.getChunkSnapshot(false, false, false) }
        // build snapshots of the slices we want to create bodies for later
        val toCreate = HashMap<IVec3, SliceSnapshot>()
        // under a lock, copy the coordinates of the Y slices we need to generate, then release
        toSnapshot.forEach { sy ->
            val pos = IVec3(sx, sy, sz)
            // we already have a body for this slice; don't process it
            if (slices.contains(pos)) return@forEach
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
            val blockShapes: Array<Shape?> = Array(16 * 16 * 16) { i ->
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

            toCreate[pos] = SliceSnapshot(
                pos = pos,
                blocks = snapshot,
                blockShapes = blockShapes,
            )
        }


    }

    override fun syncUpdate() {
        // TODO folia stuff
        world.loadedChunks.forEach { chunk ->
            chunkUpdate(chunk)
        }
    }

    override fun isTerrain(body: PhysicsBody.Read): Boolean {
        TODO("Not yet implemented")
    }

    override fun onChunksLoad(chunks: Collection<Chunk>) {}

    override fun onChunksUnload(chunks: Collection<Chunk>) {}
}
