package io.github.aecsocket.rattle.world

import io.github.aecsocket.alexandria.ItemRender
import io.github.aecsocket.alexandria.Render
import io.github.aecsocket.alexandria.Shaping
import io.github.aecsocket.alexandria.serializer.HierarchySerializer
import io.github.aecsocket.alexandria.serializer.subType
import io.github.aecsocket.alexandria.sync.Locked
import io.github.aecsocket.klam.*
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.impl.RattlePlatform
import io.github.aecsocket.rattle.impl.lineTransform
import net.kyori.adventure.key.Key

import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import org.spongepowered.configurate.objectmapping.ConfigSerializable

const val TILES_IN_SLICE = 16 * 16 * 16

abstract class DynamicTerrain<W>(
    val world: W,
    private val platform: RattlePlatform<W, *>,
    // SAFETY: while a caller has access to a DynamicTerrain object, they also have access to the containing
    // WorldPhysics, and therefore the PhysicsSpace is locked
    private val physics: PhysicsSpace,
    val settings: Settings = Settings(),
) : Destroyable {
    @ConfigSerializable
    data class Settings(
        val enabled: Boolean = true,
        val removeIn: Double = 1.0,
        val expandVelocity: Double = 0.1,
        val expandConstant: Double = 1.0,
        val layers: Layers = Layers(),
    ) {
        @ConfigSerializable
        data class Layers(
            val all: MutableMap<String, Layer> = mutableMapOf(
                "solid" to Layer.Solid(PhysicsMaterial(friction = 0.4, restitution = 0.2)),
                "fluid" to Layer.Fluid(density = 1.0),
            ),
            val defaultSolid: String = "solid",
            val defaultFluid: String = "fluid",
            val byBlock: MutableMap<Key, String> = HashMap(),
        ) {
//            val all = mutableMapOf(
//                "solid" to Layer.Solid(PhysicsMaterial(friction = 0.4, restitution = 0.2)),
//                "fluid" to Layer.Fluid(density = 1.0),
//            )
        }
    }

    sealed interface Layer {
        @ConfigSerializable
        data class Solid(
            val material: PhysicsMaterial,
        ) : Layer

        @ConfigSerializable
        data class Fluid(
            val density: Double,
        ) : Layer {
            init {
                require(density > 0.0) { "requires density > 0.0" }
            }
        }
    }

    data class Tile(
        val layerId: Int,
        val shapes: List<Compound.Child>,
    )

    data class RenderData(
        val position: DVec3,
        val transform: FAffine3,
        val render: ItemRender,
    )

    inner class Slice(val pos: IVec3) {
        var state: SliceState = SliceState.PendingScheduleSnapshot
        var remove: SliceRemove = SliceRemove.None
        var collision: SliceCollision? = null
            internal set
        val debugRenders = Shaping.box(DVec3(8.0))
            .map {
                val (from, to) = it + (pos * 16 + 8).toDouble()
                RenderData(
                    position = from,
                    transform = Shaping.lineTransform((to - from).toFloat(), platform.rattle),
                    render = createRender(pos),
                )
            }

        var debugColor: TextColor = NamedTextColor.DARK_GRAY
            set(value) {
                field = value
                debugRenders.forEach { it.render.glowColor(value) }
            }

        internal fun colliders(): List<ColliderKey> {
            return collision?.layers ?: emptyList()
        }

        fun onTrack(render: ItemRender, data: RenderData) {
            render
                .spawn(data.position)
                .transform(data.transform)
                .glowing(true)
                .glowColor(debugColor)
                .apply { platform.rattle.draw.lineItem(this) }
        }

        fun onUntrack(render: ItemRender) {
            render.despawn()
        }
    }

    sealed interface SliceRemove {
        /* TODO: Kotlin 1.9 data */ object None : SliceRemove

        data class PendingDestroy(val at: Long) : SliceRemove

        /* TODO: Kotlin 1.9 data */ object PendingRemove : SliceRemove
    }

    sealed interface SliceState {
        /* TODO: Kotlin 1.9 data */ object PendingScheduleSnapshot : SliceState

        object PendingSnapshot : SliceState

        class Snapshot(
            val tiles: Array<out Tile?>,
        ) : SliceState {
            companion object {
                val Empty = Snapshot(arrayOfNulls(TILES_IN_SLICE))
            }

            init {
                require(tiles.size == TILES_IN_SLICE) { "requires tiles.size == TILES_IN_SLICE" }
            }
        }

        /* TODO: Kotlin 1.9 data */ object Built : SliceState
    }

    data class SliceCollision(
        val layers: List<ColliderKey>,
    )

    data class ByCollider(
        val pos: IVec3,
        val layerId: Int,
    )

    inner class Slices {
        private val _map = HashMap<IVec3, Slice>()
        val map: Map<IVec3, Slice> get() = _map

        private val _byCollider = HashMap<ColliderKey, ByCollider>()
        val byCollider: Map<ColliderKey, ByCollider> get() = _byCollider

        private val dirty = HashSet<IVec3>()

        fun destroy() {
            _map.forEach { (pos) ->
                swapCollision(pos, null)
            }
            _map.clear()
            dirty.clear()
        }

        operator fun get(pos: IVec3) = _map[pos]

        operator fun contains(pos: IVec3) = _map.contains(pos)

        fun add(slice: Slice) {
            if (_map.contains(slice.pos))
                throw IllegalArgumentException("Slice already exists at ${slice.pos}")
            _map[slice.pos] = slice
        }

        fun remove(pos: IVec3) {
            swapCollision(pos, null)
            val slice = _map.remove(pos) ?: return
            slice.debugRenders.forEach { slice.onUntrack(it.render) }
        }

        fun swapCollision(pos: IVec3, collision: SliceCollision?) {
            val slice = _map[pos] ?: throw IllegalArgumentException("No slice at $pos")
            slice.colliders().forEach { collKey ->
                _byCollider -= collKey
                physics.colliders.remove(collKey)?.destroy()
            }
            slice.collision = collision
            collision?.layers?.forEachIndexed { layerId, collKey ->
                _byCollider[collKey] = ByCollider(pos, layerId)
            }
        }

        fun dirty(pos: IVec3) {
            dirty += pos
        }

        fun clean(): Set<IVec3> {
            return dirty.toSet().also { this.dirty.clear() }
        }
    }

    private val destroyed = DestroyFlag()
    val slices = Locked(Slices())
    private val activeBodyRenders = ArrayList<Render>()

    private val layers = ArrayList<Layer>()
    protected val defaultSolidLayer: Int
    protected val defaultFluidLayer: Int
    protected val layerByKey = HashMap<String, Int>()

    init {
        // layers
        settings.layers.all.forEach { (key, layer) ->
            layerByKey[key] = layers.size
            layers += layer
        }

        defaultSolidLayer = layerByKey[settings.layers.defaultSolid]
            ?: throw IllegalArgumentException("No layer key '${settings.layers.defaultSolid}' for default solid")
        defaultFluidLayer = layerByKey[settings.layers.defaultFluid]
            ?: throw IllegalArgumentException("No layer key '${settings.layers.defaultFluid}' for default fluid")

        settings.layers.byBlock.forEach { (blockKey, layerKey) ->
            if (!layerByKey.contains(layerKey))
                throw IllegalArgumentException("No layer key '$layerKey' for block $blockKey")
        }

        // collision handlers
        physics.onCollision { event ->
            if (event.state != PhysicsSpace.OnCollision.State.STOPPED) return@onCollision
            slices.withLock { slices ->
                // when removing slices, the impl auto-wakes touching bodies; here, we re-sleep them
                fun process(data: ByCollider, coll: ColliderKey) {
                    // only sleep them if this slice is about to be removed
                    if (slices[data.pos]?.remove != SliceRemove.PendingRemove) {
                        return
                    }
                    physics.colliders.read(coll)!!.parent?.let { physics.rigidBodies.write(it) }?.sleep()
                }

                slices.byCollider[event.colliderA]?.let { process(it, event.colliderB) }
                slices.byCollider[event.colliderB]?.let { process(it, event.colliderA) }
            }
        }
    }

    override fun destroy() {
        destroyed()
        slices.withLock { slices ->
            slices.destroy()
        }
    }

    protected abstract fun createRender(pos: IVec3): ItemRender

    protected abstract fun scheduleSnapshot(pos: IVec3)

    fun onPhysicsStep() {
        val toRemove = slices.withLock { it.map.keys.toMutableSet() }
        val toSnapshot = HashSet<IVec3>()

        activeBodyRenders.toSet().also { activeBodyRenders.clear() }.forEach { it.despawn() }

        physics.rigidBodies.active().forEach body@ { bodyKey ->
            val body = physics.rigidBodies.read(bodyKey) ?: return@body
            val linVel = body.linearVelocity
            body.colliders.forEach coll@ { collKey ->
                val coll = physics.colliders.read(collKey) ?: return@coll
                val bounds = expandBounds(linVel, coll)

                Shaping.box(halfExtent(bounds))
                    .map { it + midpoint(bounds) }
                    .forEach { (from, to) ->
                        val render = createRender(coll.position.translation.toInt().map { it shr 4 })
                            .spawn(from)
                            .transform(Shaping.lineTransform((to - from).toFloat(), platform.rattle))
                            .glowing(true)
                            .glowColor(NamedTextColor.GREEN)
                            .apply { platform.rattle.draw.lineItem(this) }
                        activeBodyRenders += render
                    }

                val slicePos = enclosedPoints(bounds / 16.0).toSet()
                toRemove -= slicePos
                toSnapshot += slicePos
            }
        }

        slices.withLock { slices ->
            toSnapshot.forEach { pos ->
                if (!slices.contains(pos)) {
                    val slice = Slice(pos)
                    slice.debugRenders.forEach { slice.onTrack(it.render, it) }
                    slices.add(slice)
                    slices.dirty(pos)
                }
            }

            toRemove.forEach { pos ->
                slices.dirty(pos)
            }

            transformStates(slices, toRemove)
        }
    }

    private fun transformStates(slices: Slices, toRemove: Set<IVec3>) {
        slices.clean().forEach { pos ->
            val slice = slices[pos] ?: return@forEach
            when (val remove = slice.remove) {
                is SliceRemove.None -> {
                    if (toRemove.contains(pos)) {
                        slice.remove = SliceRemove.PendingDestroy(
                            at = System.currentTimeMillis() + (settings.removeIn * 1000).toLong(),
                        )
                        slice.debugColor = NamedTextColor.RED
                    }
                }
                // we split the removal process into 2 states:
                // - once the timer runs out, and we *destroy* the slice, the collision is removed and destroyed
                //   but the ColliderKey -> Slice association is retained, so...
                // - when any rigid bodies which were colliding with the terrain collider are woken up
                //   (because the implementation will auto-wake them), we can immediately put them to sleep again
                //   just check if one of the colliders involved in the OnCollision is in our `byCollider` map
                // - only *then* can we fully remove the slice from any maps
                is SliceRemove.PendingDestroy -> {
                    slices.dirty(pos)
                    // check that we still want to delete this slice
                    if (toRemove.contains(pos)) {
                        if (System.currentTimeMillis() >= remove.at) {
                            // do NOT swap the colliders here (yet), since that would clear the `byCollider` association
                            // we need that when intercepting the OnCollision event
                            slice.colliders().forEach { collKey ->
                                physics.colliders.remove(collKey)?.destroy()
                            }
                            slice.remove = SliceRemove.PendingRemove
                        }
                    } else {
                        slice.remove = SliceRemove.None
                        slice.debugColor = NamedTextColor.GRAY
                    }
                }
                is SliceRemove.PendingRemove -> {
                    // now we can remove the slice, also removing the `byCollider` associations
                    // the collider keys are invalid, but that's fine
                    slices.remove(pos)
                    slice.remove = SliceRemove.None
                }
            }

            when (val state = slice.state) {
                is SliceState.PendingScheduleSnapshot -> {
                    slice.state = SliceState.PendingSnapshot
                    slices.dirty(pos)
                    scheduleSnapshot(pos)
                }
                is SliceState.PendingSnapshot -> {
                    // wait on the platform
                }
                is SliceState.Snapshot -> {
                    val collision = createSliceCollision(slice, state)
                    slice.state = SliceState.Built
                    slices.swapCollision(pos, collision)
                    slices.dirty(pos)
                    slice.debugColor = NamedTextColor.GRAY
                }
                is SliceState.Built -> {
                    // nothing to do
                }
            }
        }
    }

    private fun createSliceCollision(slice: Slice, state: SliceState.Snapshot): SliceCollision {
        val layerShapes = Array<MutableList<Compound.Child>>(layers.size) { ArrayList() }

        // assuming we're processing a slice at (1, 1, 1)...
        //  - collider at (16, 16, 16)
        //  - compound children:
        //    - stone block at (1, 1, 1)
        //      - compound child box, half extents (0.5), at (1.5, 1.5, 1.5)

        state.tiles.forEachIndexed { i, tile ->
            tile ?: return@forEachIndexed
            val localPos = posInChunk(i).toDouble() + 0.5
            val layer = layerShapes[tile.layerId]

            val shapes = tile.shapes.map { child ->
                Compound.Child(
                    shape = child.shape,
                    delta = child.delta * DIso3(localPos),
                )
            }
            layer += shapes
        }

        return SliceCollision(
            layers = layerShapes.mapIndexedNotNull { i, shapes ->
                if (shapes.isEmpty()) return@mapIndexedNotNull null
                val layer = layers[i]

                val coll = platform.rattle.engine.createCollider(
                    shape = platform.rattle.engine.createShape(Compound(shapes)),
                    position = StartPosition.Absolute(
                        DIso3((slice.pos * 16).toDouble()),
                    )
                )
                    .handlesEvents(ColliderEvent.COLLISION)
                when (layer) {
                    is Layer.Solid -> {
                        coll.physicsMode(PhysicsMode.SOLID)
                        coll.material(layer.material)
                    }
                    is Layer.Fluid -> {
                        coll.physicsMode(PhysicsMode.SENSOR)
                    }
                }
                physics.colliders.add(coll)
            },
        )
    }

    private fun expandBounds(linVel: DVec3, coll: Collider): DAabb3 {
        val from = coll.position.translation
        val to = from + linVel * settings.expandVelocity
        val collBound = coll.bounds()

        return expand(
            expand(
                DAabb3(
                    min(from, to),
                    max(from, to),
                ), // a box spanning the current coll pos, up to a bit in front of it (determined by velocity)
                (collBound.max - collBound.min) / 2.0,
            ), // that velocity box, expanded by the actual collider bounds
            DVec3(settings.expandConstant), // that box, expanded by the constant factor
        )
    }

    fun onSliceUpdate(pos: IVec3) {
        // wake any bodies which intersect this slice
        // note that this will not generate the slice collision *right now*; it will be scheduled on the next update
        val min = (pos * 16).toDouble()
        physics.query.intersectBounds(DAabb3(min, min + 16.0)) { collKey ->
            physics.colliders.read(collKey)?.let { coll ->
                val parent = coll.parent?.let { physics.rigidBodies.write(it) } ?: return@let
                parent.wakeUp()
            }
            QueryResult.CONTINUE
        }

        // if a slice already exists for this position, schedule an update for its collision shape
        slices.withLock { slices ->
            val slice = slices[pos] ?: return@withLock
            slice.debugColor = NamedTextColor.AQUA
            when (slice.state) {
                is SliceState.PendingScheduleSnapshot -> {}
                is SliceState.PendingSnapshot -> {}
                else -> {
                    slice.state = SliceState.PendingScheduleSnapshot
                    slices.dirty(pos)
                    scheduleSnapshot(pos)
                }
            }
        }
    }

    protected fun setSliceSnapshot(pos: IVec3, snapshot: SliceState.Snapshot) {
        slices.withLock { slices ->
            // if the slice has been destroyed by the time we've snapshot it, silently fail
            val slice = slices[pos] ?: return@withLock
            when (slice.state) {
                // or if it's in an invalid state, just silently fail
                is SliceState.PendingSnapshot -> {
                    slice.state = snapshot
                    slices.dirty(pos)
                }
                else -> {
                    // silently fail
                }
            }
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

val terrainLayerSerializer = HierarchySerializer {
    subType<_, DynamicTerrain.Layer.Solid>("solid")
    subType<_, DynamicTerrain.Layer.Fluid>("fluid")
}
