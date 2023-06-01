package io.github.aecsocket.rattle.world

import io.github.aecsocket.alexandria.log.info
import io.github.aecsocket.alexandria.sync.Locked
import io.github.aecsocket.klam.*
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.impl.RattleHook
import java.lang.Exception

interface TerrainLayer {
    object Solid : TerrainLayer

    object Fluid : TerrainLayer // TODO
}

const val BLOCKS_IN_SECTION = 16 * 16 * 16

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
 * - Store the chunk coordinate and chunk snapshot to be processed later ([toCreate])
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
 * - a variable factor defined as a multiplier of a body's current velocity
 *
 * # Structures
 *
 * For solid blocks, the strategy builds up a collider per block, using a compound shape if necessary to
 * represent the block's shape, and adds it as a solid collider.
 *
 * For fluids, the strategy builds up a collider as for blocks, but adds it as a sensor collider. This sensor
 * can later be queried to see what bodies are touching it, and to apply buoyancy forces.
 */
abstract class DynamicTerrain(
    private val rattle: RattleHook,
    // SAFETY: we only access the physics while the containing WorldPhysics is locked
    val physics: PhysicsSpace,
) : TerrainStrategy {
    data class SectionLayer(
        val terrain: TerrainLayer,
        val colliders: List<ColliderHandle>,
    )

    sealed interface Section {
        val pos: IVec3

        data class Pending(
            override val pos: IVec3,
        ) : Section

        class Snapshot(
            override val pos: IVec3,
            val blocks: Array<out Block>,
        ) : Section {
            init {
                require(blocks.size == BLOCKS_IN_SECTION) { "requires blocks.size == BLOCKS_IN_SECTION" }
            }
        }

        data class Built(
            override val pos: IVec3,
            val layers: Map<TerrainLayer, SectionLayer>,
        ) : Section {
            fun colliders(): List<ColliderHandle> =
                layers.flatMap { (_, layer) -> layer.colliders }
        }
    }

    private inner class Sections {
        private val mMap: MutableMap<IVec3, Section> = HashMap()
        val map: Map<IVec3, Section>
            get() = mMap

        operator fun get(pos: IVec3) = mMap[pos]

        fun add(section: Section) {
            mMap[section.pos] = section
        }

        fun remove(pos: IVec3): Section? {
            val section = mMap.remove(pos) ?: return null
            return section
        }

        fun destroy() {
            mMap.forEach { (_, section) ->
                when (section) {
                    is Section.Built -> {
                        section.colliders().forEach { coll ->
                            physics.colliders.remove(coll)?.destroy()
                        }
                    }
                    else -> {}
                }
            }
            mMap.clear()
        }
    }

    private var enabled = true

    private val sections = Locked(Sections())
    protected val toCreate = Locked(HashSet<IVec3>())

    // cached fields, to avoid reallocating all the time
    // SAFETY: the update methods will only be called in a specific order (hopefully),
    // so these fields will not be modified in unexpected ways
    private val toRemove = HashSet<IVec3>()
    private val toSnapshot = HashSet<IVec3>()

    protected abstract fun scheduleToSnapshot(sectionPos: Iterable<IVec3>)

    override fun enable() {
        enabled = true
    }

    override fun disable() {
        enabled = false
    }

    override fun destroy() {
        sections.withLock { sections ->
            sections.destroy()
        }
    }

    override fun onPhysicsStep() {
        if (!enabled) return

        toRemove.clear()
        // clone the key set, so it's not modified while we're using it
        toRemove += sections.withLock { it.map.keys.toMutableSet() }
        toSnapshot.clear()

        // find chunk sections which bodies are intersecting
        fun forCollider(body: RigidBody.Read, coll: Collider.Read) {
            val bounds = computeBounds(body, coll)
            val sectionPos = enclosedPoints(bounds / 16.0).toSet()

            toRemove -= sectionPos
            // only schedule snapshotting for sections that aren't already created or going to be generated
            sections.withLock { sections ->
                sectionPos.forEach { pos ->
                    if (!sections.map.contains(pos)) {
                        sections.add(Section.Pending(pos))
                        toSnapshot += pos
                    }
                }
            }
        }

        physics.bodies.active().forEach { bodyKey ->
            physics.bodies.read(bodyKey)?.let { body ->
                body.colliders.forEach { collKey ->
                    physics.colliders.read(collKey)?.let { coll ->
                        forCollider(body, coll)
                    }
                }
            }
        }

        // clone the toSnapshot set and send the positions off to be snapshot
        scheduleToSnapshot(toSnapshot.toSet())

        // fetch and clear the pending positions of snapshots to build
        val toCreate = toCreate.withLock { toCreate ->
            toCreate.toList().also { toCreate.clear() }
        }

        sections.withLock { sections ->
            // remove old sections
            toRemove.forEach { pos ->
                sections.remove(pos)?.let {
                    destroySection(it)
                }
            }

            // build up new sections
            toCreate.forEach { pos ->
                when (val section = sections[pos]) {
                    is Section.Snapshot -> sections.add(createSection(section))

                }
            }
        }
    }

    private fun computeBounds(body: RigidBody.Read, coll: Collider.Read): Aabb {
        val bounds = coll.bounds()
        // TODO constant scaling
        // TODO velocity scaling
        return bounds
    }

    private fun createSection(snapshot: SectionSnapshot): Section {
        val start = System.nanoTime()

        class SectionLayerData {
            val colliders = ArrayList<ColliderHandle>()
        }

        val layers = HashMap<TerrainLayer, SectionLayerData>()
        snapshot.blocks.forEachIndexed { i, b ->
            val block = b as? Block.Shaped ?: return@forEachIndexed
            val (x, y, z) = (snapshot.pos * 16) + posInChunk(i)

            val coll = rattle.engine.createCollider(
                shape = block.shape,
                material = PhysicsMaterial(friction = 0.5, restitution = 0.2), // TODO
                position = Iso(
                    DVec3(x.toDouble() + 0.5, y.toDouble() + 0.5, z.toDouble() + 0.5),
                ),
                mass = Mass.Density(1.0), // TODO
                physics = when (block) {
                    is Block.Solid -> PhysicsMode.SOLID
                    is Block.Fluid -> PhysicsMode.SENSOR
                },
            ).let { physics.colliders.add(it) }
            println(" · adding coll $coll")

            val layer = when (block) {
                is Block.Solid -> TerrainLayer.Solid
                is Block.Fluid -> TerrainLayer.Fluid
            }
            val layerData = layers.computeIfAbsent(layer) { SectionLayerData() }
            layerData.colliders += coll
        }

        val end = System.nanoTime()
        rattle.log.info { "Created section ${snapshot.pos} in ${(end - start) / 1.0e6} ms" }
        return Section(
            pos = snapshot.pos,
            layers = layers.map { (terrainLayer, data) ->
                terrainLayer to SectionLayer(terrainLayer, data.colliders)
            }.associate { it },
        )
    }

    private fun destroySection(section: Section) {
        section.colliders().forEach { coll ->
            physics.colliders.remove(coll)
        }
    }
}

fun posInChunk(i: Int) = IVec3(
    (i / 16 / 16) % 16,
    (i / 16) % 16,
    i % 16,
)

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
