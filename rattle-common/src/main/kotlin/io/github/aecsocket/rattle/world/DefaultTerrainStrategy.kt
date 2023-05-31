package io.github.aecsocket.rattle.world

import io.github.aecsocket.alexandria.sync.Locked
import io.github.aecsocket.klam.*
import io.github.aecsocket.rattle.*

/**
 * The default implementation of [TerrainStrategy], which dynamically creates terrain collision
 * just-in-time for physics bodies to collide with them.
 *
 * # Strategy
 *
 * The process occurs concurrently, with the first step starting on a physics thread.
 *
 * ## Physics thread - before step
 *
 * For each active rigid body...
 * - Compute its AABB bound in world-space
 * - Expand that AABB slightly (detailed under Expansion)
 * - Compute which chunk sections that AABB intersects, inclusively
 * - Store the list of chunk section coordinates ([IVec3]s) to be processed later ([toSnapshot])
 *
 * ## Chunk thread
 *
 * This process is ran for every loaded chunk in the world.
 *
 * *Note:* the exact thread that this runs on is an implementation detail. On platforms with a
 * single main thread (Fabric, Paper), this will be the main tick thread. On platforms where chunks
 * are processed concurrently (Folia), this will be the thread responsible for the current chunk.
 *
 * - Fetch the list of chunk section coordinates
 * - Find all section coordinates for the current chunk
 * - Generate a chunk snapshot
 * - Store the chunk coordinate and chunk snapshot to be processed later ([toBuild])
 *
 * ## Physics thread - before step
 *
 * - Fetch the chunk snapshots to process
 * - Iterate through all blocks in the chunk snapshot to build up the appropriate physics structures
 *   (detailed under Structures)
 * - Add the structures to the physics state and store them internally for queries
 *
 * For each chunk section coordinate from the previous step that was *not* recomputed in the first step,
 * the section's corresponding body and colliders are removed from the physics state.
 *
 * # Expansion
 *
 * The amount that the AABB is expanded by is determined by:
 * - a constant factor defined in the settings - this is a single scalar on which each axis of the AABB
 *   is expanded by. This acts as a "radius" rather than a "diameter" of the box.
 *
 * # Structures
 *
 * For solid blocks, the strategy builds up a collider per block, using a compound shape if necessary to
 * represent the block's shape, and adds it as a solid collider.
 *
 * For fluids, the strategy builds up a collider as for blocks, but adds it as a sensor collider. This sensor
 * can later be queried to see what bodies are touching it, and to apply buoyancy forces.
 */
abstract class DefaultTerrainStrategy<S>(
    // SAFETY: we only access the physics while the containing WorldPhysics is locked
    val physics: PhysicsSpace,
) : TerrainStrategy {
    interface Tile {
        val isPassable: Boolean
        val isFluid: Boolean

        fun shape(): Shape?
    }

    data class TileSnapshot(
        val shape: Shape,
    )

    class Slice(
    ) {
        fun colliders(): List<Collider>
    }

    private class Slices {
        private val mSlices: MutableMap<IVec3, Slice> = HashMap()
        val slices: Map<IVec3, Slice>
            get() = mSlices
        private val mColliderToSlice: MutableMap<Collider, Slice> = HashMap()
        val colliderToSlice: Map<Collider, Slice>
            get() = mColliderToSlice

        fun remove(pos: IVec3): Slice? {
            val slice = mSlices.remove(pos) ?: return null
            slice.colliders().forEach { mColliderToSlice.remove(it) }
            return slice
        }
    }

    @JvmInline
    value class TileSnapshots(val tiles: Array<TileSnapshot?>)

    class SliceSnapshot(
        val pos: IVec3,
        val tiles: TileSnapshots,
    )

    private var enabled = true

    private val slices = Locked(Slices())
    private val toBuild = Locked(ArrayList<SliceSnapshot>())

    // cached fields, to avoid reallocating all the time
    // SAFETY: the update methods will only be called in a specific order (hopefully),
    // so these fields will not be modified in unexpected ways
    private val toRemove = HashSet<IVec3>()
    private val toSnapshot = ArrayList<IVec3>()

    protected abstract fun scheduleToSnapshot(sliceCoords: List<IVec3>)

    protected abstract fun S.at(lx: Int, ly: Int, lz: Int): Tile

    override fun enable() {
        enabled = true
    }

    override fun disable() {
        enabled = false
    }

    override fun onPhysicsStep() {
        if (!enabled) return

        // find chunk slices which bodies are intersecting
        toRemove.clear()
        // clone the key set, so it's not modified while we're using it
        toRemove += slices.withLock { it.slices.keys.toMutableSet() }
        toSnapshot.clear()

        fun forCollider(coll: Collider.Access) {
            val bounds = computeBounds(coll)
            val sliceCoords = enclosedPoints(bounds / 16.0).toSet()
            toRemove -= sliceCoords
            toSnapshot += sliceCoords
        }

        physics.bodies.active().forEach { aBody ->
            aBody.read { body ->
                body.colliders.forEach { aColl ->
                    aColl.read { coll ->
                        forCollider(coll)
                    }
                }
            }
        }

        // schedule bodies to be created
        scheduleToSnapshot(toSnapshot)

        // remove old slices
        slices.withLock { slices ->
            toRemove.forEach { pos ->
                slices.remove(pos)?.colliders()?.forEach { physics.colliders.remove(it) }
            }
        }

        // build up new slices
        // fetch and clear the pending snapshots to build
        val toBuild = toBuild.withLock { toBuild ->
            toBuild.toList().also { toBuild.clear() }
        }
        buildSliceBodies(toBuild)
    }

    fun createSliceSnapshot(pos: IVec3, slice: S): SliceSnapshot? {
        if (slices.withLock { it.slices.contains(pos) }) {
            // we already have a body for this slice
            return null
        }

        val tiles: Array<TileSnapshot?> = Array(16 * 16 * 16) { i ->
            val lx = (i / 16 / 16) % 16
            val ly = (i / 16) % 16
            val lz = i % 16
            snapshotTile(slice.at(lx, ly, lz))
        }

        return SliceSnapshot(
            pos = pos,
            tiles = TileSnapshots(tiles),
        )
    }

    private fun computeBounds(coll: Collider.Access): Aabb {
        val bounds = coll.bounds()
        return bounds
    }

    private fun snapshotTile(tile: Tile): TileSnapshot? {
        return when {
            tile.isPassable -> null
            tile.isFluid -> null // TODO!!
            else -> tile.shape()?.let { TileSnapshot(it) }
        }
    }

    private fun buildSliceBodies(snapshots: List<SliceSnapshot>) {
        snapshots.forEach { snapshot ->
        }
    }
}

private fun enclosedPoints(b: DAabb3): Iterable<IVec3> {
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
