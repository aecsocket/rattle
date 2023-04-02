package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.ignacio.*
import io.github.aecsocket.klam.*
import org.bukkit.Chunk
import org.bukkit.ChunkSnapshot
import org.bukkit.World

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
    private val engine: IgnacioEngine,
    private val world: World,
    private val physics: PhysicsSpace,
) : TerrainStrategy {
    data class SliceSnapshot(
        val pos: IVec3,
        val blocks: ChunkSnapshot,
    )

    data class Slice(
        val layers: Map<TerrainLayer, PhysicsBody>,
    )

    private val destroyed = DestroyFlag()
    private val cube = engine.shape(BoxGeometry(FVec3(0.5f)))
    private val startY = world.minHeight
    private val numSlices = (world.maxHeight - startY) / 16
    private val negativeYSlices = -startY / 16

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

    private val toSnapshot: MutableSet<IVec3> = HashSet()
    private var toRemove: Set<IVec3> = emptySet()
    private val toCreate: MutableMap<IVec3, SliceSnapshot> = HashMap()

    var enabled = true
        private set

    override fun destroy() {
        destroyed.mark()
        cube.destroy()
    }

    override fun enable() {
        enabled = true
    }

    override fun disable() {
        enabled = false
    }

    internal fun updatePhysics() {
        if (!enabled) return
        // make sets of which slice positions we need to generate, and which to remove
        val toRemove = HashSet<IVec3>() // todo
        val toSnapshot = HashSet<IVec3>()
        physics.bodies.active().forEach { bodyId ->
            bodyId.readUnlocked { body ->
                // only create slices for moving objects
                // TODO custom layer support: make this variable
                if (body.contactFilter.layer == engine.layers.moving) return@readUnlocked
                val overlappingSlices = enclosedPoints(body.bounds / 16.0).toSet()
                toSnapshot += overlappingSlices
                toRemove -= overlappingSlices
            }
        }

        this.toRemove = toRemove
        synchronized(this.toSnapshot) {
            this.toSnapshot += toSnapshot
        }
    }

    internal fun updateChunk(cx: Int, cz: Int) {
        if (!enabled) return
        // create snapshots for positions
        val toCreate = HashMap<IVec3, SliceSnapshot>()
        // under a lock, copy the entire set, then release
        // this is safe since our IVec3's here won't be modified
        synchronized(toSnapshot) { toSnapshot.toSet() }.forEach { slicePos ->

        }
    }

    override fun isTerrain(body: PhysicsBody.Read): Boolean {
        TODO("Not yet implemented")
    }

    override fun onChunksLoad(chunks: Collection<Chunk>) {}

    override fun onChunksUnload(chunks: Collection<Chunk>) {}
}
