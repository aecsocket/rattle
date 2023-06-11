package io.github.aecsocket.rattle.world

import io.github.aecsocket.alexandria.sync.Locked
import io.github.aecsocket.alexandria.sync.Sync
import io.github.aecsocket.klam.*
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.impl.RattleHook
import org.spongepowered.configurate.objectmapping.ConfigSerializable

const val TILES_IN_SLICE = 16 * 16 * 16

/**
 * The default implementation of [TerrainStrategy], which dynamically creates terrain collision
 * just-in-time for physics bodies to collide with them.
 *
 * # Strategy
 *
 * A world is already split into chunks by the game itself, but we take that one step further and
 * split chunks into "sections" - 16x16x16 parts of a chunk. The strategy processes each section
 * independently, and stores section data in a `Map<IVec3, Section>`. This processing starts on
 * the physics thread (before the physics step):
 *
 * ## Physics thread
 *
 * For each active rigid body...
 * - Compute its AABB bound in world-space
 * - Expand that AABB slightly (detailed under Expansion)
 * - Compute which sections that AABB intersects, inclusively, as a set of [IVec3]s
 * - Any new positions that aren't in the section map yet?
 *   - Set that section to [SectionState.Pending]
 *   - Send it over to the platform implementation to process into a [SectionState.Snapshot]
 * - Any positions that were in the section map but not anymore?
 *   - Mark that section as "to be destroyed" in the future
 *
 * ## Snapshotting
 *
 * This process is platform-dependent, runs during [scheduleToSnapshot], and should iterate
 * over all chunk positions passed to it. On platforms with a single main thread (Fabric, Paper),
 * this will run on the main tick thread. On platforms where chunks are processed concurrently (Folia),
 * this will run on the thread responsible for the current chunk.
 *
 * For each section position...
 * - Generate a chunk snapshot (platform-dependent)
 *   - Typically involves getting block types ([Block] passable, fluid, etc.) and shapes
 * - Update that section position with [SectionState.Snapshot]
 *
 * ## Physics thread
 *
 * - Fetch the chunk snapshots to process
 * - Iterate through all blocks in the chunk snapshot to build up the appropriate physics structures
 *   (detailed under Structures), creating a [SectionState.Built]
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
 * For [Block.Solid], the strategy builds up a collider per block, using a compound shape if necessary to
 * represent the block's shape, and adds it as a solid collider.
 *
 * For [Block.Fluid], the strategy builds up a collider as for blocks, but adds it as a sensor collider. This sensor
 * can later be queried to see what bodies are touching it, and to apply buoyancy forces. (TODO)
 */
abstract class Abcd(
    private val rattle: RattleHook,
    // SAFETY: we only access the physics while the containing WorldPhysics is locked
    val physics: Sync<PhysicsSpace>,
    val settings: Settings = Settings(),
) : TerrainStrategy {
    @ConfigSerializable
    data class Settings(
        val removeTime: Double = 0.5,
        val expansion: Expansion = Expansion(),
    ) {
        @ConfigSerializable
        data class Expansion(
            val velocityFactor: Real = 0.1,
            val constant: Real = 4.0,
        )
    }

    /*
    v2:
      - Each block type has an associated Layer
      - Multiple blocks can have the same Layer
      - Layer describes material + sensor or not
      - E.g:
        - stone -> Layer(<bumpy>, solid)
        - ice -> Layer(<slippery>, solid)
        - water -> Layer(<water-y>, sensor)
      - All Layer instances are known in advance, in a List<Layer>

      - Blocks (solid + fluid) in a section are greedily combined into an Array<greedy blocks>
        - Index determines the index of LayerDesc in that list above
        - 1 collider per "greedy blocks" object
     */

    sealed interface Layer {
        data class Solid(val material: PhysicsMaterial) : Layer

        data class Fluid(val density: Real) : Layer
    }

    data class Block(
        val layerId: Int,
        val compoundChildren: List<Compound.Child>,
    )

    inner class Section(val pos: IVec3) {
        var state: SectionState = SectionState.Pending
        var data: SectionData? = null
            private set
        private var removeAt = -1L

        fun toBeRemoved() = removeAt >= 0

        fun toRemoveNow() = toBeRemoved() && System.currentTimeMillis() >= removeAt

        fun removeIn(ms: Long) {
            removeAt = System.currentTimeMillis() + ms
        }

        fun stopRemoval() {
            removeAt = -1
        }

        fun destroy() {
            val data = data ?: return
            data.layers.forEach { collKey ->
                // TODO: when removing, any bodies touching the terrain will be woken up
                // we need to avoid this, because it means the next update, the terrain will be recreated
                // since the bodies will be awake, and processed by the system
                physics.withLock { physics -> physics.colliders.remove(collKey) }?.destroy()
            }
        }

        fun swapData(newData: SectionData?) {
            destroy()
            data = newData
        }
    }

    data class SectionData(
        val layers: List<ColliderKey>,
    )

    sealed interface SectionState {
        /* TODO: Kotlin 1.9 data */ object Pending : SectionState

        class Snapshot(
            val blocks: Array<out Block?>,
        ) : SectionState {
            init {
                require(blocks.size == TILES_IN_SLICE) { "requires blocks.size == BLOCKS_IN_SECTION" }
            }
        }

        /* TODO: Kotlin 1.9 data */ object Built : SectionState
    }

    inner class Sections {
        private val mMap: MutableMap<IVec3, Section> = HashMap()
        val map: Map<IVec3, Section>
            get() = mMap

        private val mDirty = HashSet<IVec3>()

        operator fun get(pos: IVec3) = mMap[pos]

        fun add(section: Section) {
            val pos = section.pos
            if (mMap.contains(pos))
                throw IllegalStateException("Already contains a section for $pos")
            mMap[pos] = section
            mDirty += pos
        }

        fun remove(pos: IVec3): Section? {
            val section = mMap.remove(pos) ?: return null
            section.destroy()
            return section
        }

        fun dirty(pos: IVec3) {
            mDirty += pos
        }

        fun clean(): Set<IVec3> {
            return mDirty.toSet().also { mDirty.clear() }
        }

        fun destroy() {
            mMap.forEach { (_, section) ->
                section.destroy()
            }
            mMap.clear()
            mDirty.clear()
        }
    }

    private var enabled = true

    val sections = Locked(Sections())

    protected abstract val layers: Array<out Layer>

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
        physics.withLock { physics ->
            // TODO we could probably turn this into a debug fn
            // where would we put it on the HUD?
//        println("sections:")
//        sections.withLock { s ->
//            s.map.forEach { (pos, sc) ->
//                println("  $pos = $sc")
//            }
//        }

            val toSnapshot = HashSet<IVec3>()
            // clone the key set, so it's not modified while we're using it
            val toRemove = sections.withLock { it.map.keys.toMutableSet() }

            // find chunk sections which bodies are intersecting
            fun forCollider(linVel: Vec, coll: Collider) {
                val bounds = computeBounds(linVel, coll)
                val sectionPos = enclosedPoints(bounds / 16.0).toSet()

                toRemove -= sectionPos
                // only schedule snapshotting for sections that aren't already created or going to be generated
                sections.withLock { sections ->
                    sectionPos.forEach { pos ->
                        if (!sections.map.contains(pos)) {
                            sections.add(Section(pos))
                            toSnapshot += pos
                        }
                    }
                }
            }

            physics.rigidBodies.active().forEach body@ { bodyKey ->
                val body = physics.rigidBodies.read(bodyKey) ?: return@body
                val linVel = body.linearVelocity
                body.colliders.forEach collider@ { collKey ->
                    val coll = physics.colliders.read(collKey) ?: return@collider
                    forCollider(linVel, coll)
                }
            }

            // consume toSnapshot; clone the toSnapshot set and send the positions off to be snapshot
            scheduleToSnapshot(toSnapshot)

            // process all dirty sections and turn them into their next state
            sections.withLock { sections ->
                // needed so that the `.clean()` call below will have all of our toRemove sections
                toRemove.forEach { pos ->
                    sections.dirty(pos)
                }

                // fetch and clear; once we start working, all sections are clean,
                // and while we work we mark them as dirty
                sections.clean().forEach { pos ->
                    val section = sections[pos] ?: return@forEach
                    if (toRemove.contains(pos)) {
                        if (section.toBeRemoved()) {
                            if (section.toRemoveNow()) {
                                sections.remove(pos)
                            }
                        } else {
                            section.removeIn((settings.removeTime * 1000).toLong())
                        }
                    } else if (section.toBeRemoved()) {
                        // we no longer want to remove it
                        section.stopRemoval()
                    }

                    when (val state = section.state) {
                        is SectionState.Pending -> {}
                        is SectionState.Snapshot -> {
                            section.swapData(createSection(pos, state))
                            section.state = SectionState.Built
                            sections.dirty(pos)
                        }
                        is SectionState.Built -> {}
                    }
                }
            }
        }
    }

    private fun computeBounds(linVel: Vec, coll: Collider): Aabb {
        val from = coll.position.translation
        val to = from + linVel * settings.expansion.velocityFactor
        val collBound = coll.bounds()

        return expand(
            expand(
                Aabb(
                    min(from, to),
                    max(from, to),
                ), // a box spanning the current coll pos, up to a bit in front of it (determined by velocity)
                (collBound.max - collBound.min) / 2.0,
            ), // that velocity box, expanded by the actual collider bounds
            DVec3(settings.expansion.constant), // that box, expanded by the constant factor
        )
    }

    private fun createSection(pos: IVec3, snapshot: SectionState.Snapshot): SectionData {
        val layerChildren = Array<MutableList<Compound.Child>>(layers.size) { ArrayList() }
        snapshot.blocks.forEachIndexed { i, block ->
            block ?: return@forEachIndexed
            val (lx, ly, lz) = posInChunk(i)

            val children = block.compoundChildren.map { child ->
                Compound.Child(
                    child.shape,
                    child.delta.copy(
                        translation = child.delta.translation + Vec(lx.toDouble(), ly.toDouble(), lz.toDouble()) + 0.5,
                    ),
                )
            }
            layerChildren[block.layerId] += children
        }

        val layers = layerChildren.mapIndexedNotNull { layerId, children ->
            if (children.isEmpty()) return@mapIndexedNotNull null
            val layer = layers[layerId]
            // SAFETY: this collider owns this compound shape
            rattle.engine.createCollider(rattle.engine.createShape(Compound(children)))
                .position(Iso(Vec(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())))
                .also {
                    when (layer) {
                        is Layer.Solid -> it
                            .material(layer.material)
                            .physicsMode(PhysicsMode.SOLID)
                        is Layer.Fluid -> it
                            .physicsMode(PhysicsMode.SENSOR)
                    }
                }
                .mass(Mass.Infinite)
                .let { physics.withLock { physics -> physics.colliders.add(it) } }
        }

        return SectionData(
            layers = layers,
        )
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
