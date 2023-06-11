package io.github.aecsocket.rattle.paper

import io.github.aecsocket.alexandria.desc.ParticleShaping
import io.github.aecsocket.alexandria.paper.extension.toColor
import io.github.aecsocket.alexandria.sync.Locked
import io.github.aecsocket.klam.*
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.world.TILES_IN_SLICE
import io.github.aecsocket.rattle.world.TerrainStrategy
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Chunk
import org.bukkit.Particle
import org.bukkit.Particle.DustOptions
import org.bukkit.World
import org.spongepowered.configurate.objectmapping.ConfigSerializable

private val airTiles = arrayOfNulls<PaperDynamicTerrain.Tile?>(TILES_IN_SLICE)

private val debugColliderBound = PaperDynamicTerrain.DebugInfo(NamedTextColor.WHITE, 0.0)
private val debugNewSlice = PaperDynamicTerrain.DebugInfo(NamedTextColor.BLUE, 0.25)
private val debugPersistSlice = PaperDynamicTerrain.DebugInfo(NamedTextColor.YELLOW, 0.0)
private val debugRemoveSlice = PaperDynamicTerrain.DebugInfo(NamedTextColor.RED, 0.25)

class PaperDynamicTerrain(
    private val rattle: PaperRattle,
    val physics: PhysicsSpace,
    private val world: World,
    val settings: Settings = Settings(),
) : TerrainStrategy {
    @ConfigSerializable
    data class Settings(
        val removeIn: Double = 0.5,
        val expandVelocity: Real = 0.1,
        val expandConstant: Real = 1.0,
    )

    data class DebugInfo(
        val color: TextColor,
        val offset: Real,
    )

    interface Tile {
        val shapes: List<Compound.Child>
    }

    inner class Slice(val pos: IVec3, var state: SliceState) {
        var removeAt = -1L
        var data: SliceData? = null
            private set

        fun destroyData() {
            val data = data ?: return
            data.layers.forEach { collKey ->
                physics.colliders.remove(collKey)
            }
        }

        fun swapData(value: SliceData?) {
            destroyData()
            data = value
        }

        fun toRemove() = removeAt >= 0

        fun toRemoveNow() = toRemove() && System.currentTimeMillis() >= removeAt

        fun removeIn(ms: Long) {
            removeAt = System.currentTimeMillis() + ms
        }

        fun stopRemoval() {
            removeAt = -1
        }
    }

    sealed interface SliceState {
        /* TODO: Kotlin 1.9 data */ object PendingScheduleSnapshot : SliceState

        object PendingSnapshot : SliceState

        class Snapshot(
            val tiles: Array<out Tile?>,
        ) : SliceState {
            init {
                require(tiles.size == TILES_IN_SLICE) { "requires tiles.size == TILES_IN_SLICE" }
            }
        }

        /* TODO: Kotlin 1.9 data */ object Built : SliceState
    }

    data class SliceData(
        val layers: List<ColliderKey>,
    )

    class Slices {
        private val map = HashMap<IVec3, Slice>()
        private val dirty = HashSet<IVec3>()

        fun all(): Map<IVec3, Slice> = map

        operator fun get(pos: IVec3) = map[pos]

        operator fun contains(pos: IVec3) = map.contains(pos)

        fun add(slice: Slice) {
            if (map.contains(slice.pos))
                throw IllegalArgumentException("Slice already exists at ${slice.pos}")
            map[slice.pos] = slice
        }

        fun remove(pos: IVec3) {
            map.remove(pos)?.destroyData()
        }

        fun dirty(pos: IVec3) {
            dirty += pos
        }

        fun clean(): Set<IVec3> {
            return dirty.toSet().also { this.dirty.clear() }
        }
    }

    private val slices = Locked(Slices())

    override fun destroy() {
    }

    override fun enable() {

    }

    override fun disable() {

    }

    override fun onPhysicsStep() {
        val toRemove = slices.withLock { it.all().keys.toMutableSet() }
        val toSnapshot = HashSet<IVec3>()

        physics.rigidBodies.active().forEach body@ { bodyKey ->
            val body = physics.rigidBodies.read(bodyKey) ?: return@body
            val linVel = body.linearVelocity
            body.colliders.forEach coll@ { collKey ->
                val coll = physics.colliders.read(collKey) ?: return@coll
                val bounds = expandBounds(linVel, coll)
                drawDebugAabb(bounds, debugColliderBound)
                val slicePos = enclosedPoints(bounds / 16.0).toSet()
                toRemove -= slicePos
                toSnapshot += slicePos
            }
        }

        slices.withLock { slices ->
            toSnapshot.forEach { pos ->
                if (slices.contains(pos)) {
                    drawDebugAabb(sliceBounds(pos), debugPersistSlice)
                } else {
                    drawDebugAabb(sliceBounds(pos), debugNewSlice)
                    slices.add(Slice(pos, SliceState.PendingScheduleSnapshot))
                    slices.dirty(pos)
                }
            }

            toRemove.forEach { pos ->
                drawDebugAabb(sliceBounds(pos), debugRemoveSlice)
                slices.dirty(pos)
            }

            transformStates(slices, toRemove)
        }
    }

    private fun transformStates(slices: Slices, toRemove: Set<IVec3>) {
        slices.clean().forEach { pos ->
            val slice = slices[pos] ?: return@forEach
            if (toRemove.contains(pos)) {
                slices.dirty(pos)
                if (!slice.toRemove()) {
                    slice.removeIn((settings.removeIn * 1000).toLong())
                }
                if (slice.toRemoveNow()) {
                    slices.remove(pos)
                }
            } else {
                slice.stopRemoval()
            }

            when (val state = slice.state) {
                is SliceState.PendingScheduleSnapshot -> {
                    scheduleSnapshot(pos)
                    slice.state = SliceState.PendingSnapshot
                    slices.dirty(pos)
                }
                is SliceState.PendingSnapshot -> {
                    // wait on the platform
                }
                is SliceState.Snapshot -> {
                    val sliceData = createSliceData(slice, state)
                    slice.state = SliceState.Built
                    slice.swapData(sliceData)
                    slices.dirty(pos)
                }
                is SliceState.Built -> {
                    // nothing to do
                }
            }
        }
    }

    private fun createSliceData(slice: Slice, state: SliceState.Snapshot): SliceData {
        // todo
        return SliceData(
            layers = ArrayList(),
        )
    }

    private fun expandBounds(linVel: Vec, coll: Collider): Aabb {
        val from = coll.position.translation
        val to = from + linVel * settings.expandVelocity
        val collBound = coll.bounds()

        return expand(
            expand(
                Aabb(
                    min(from, to),
                    max(from, to),
                ), // a box spanning the current coll pos, up to a bit in front of it (determined by velocity)
                (collBound.max - collBound.min) / 2.0,
            ), // that velocity box, expanded by the actual collider bounds
            DVec3(settings.expandConstant), // that box, expanded by the constant factor
        )
    }

    private fun drawDebugAabb(aabb: Aabb, info: DebugInfo) {
        ParticleShaping().aabb(aabb).forEach { (x, y, z) ->
            world.players.forEach { player ->
                player.spawnParticle(
                    Particle.REDSTONE,
                    x, y + info.offset, z,
                    0,
                    1.0, 1.0, 1.0,
                    10000.0, DustOptions(info.color.toColor(), 1.0f),
                )
            }
        }
    }

    private fun scheduleSnapshot(pos: IVec3) {
        val chunk = world.getChunkAt(pos.x, pos.z)
        rattle.scheduling.onChunk(chunk).launch {
            val snapshot = createSnapshot(chunk, pos)
            slices.withLock { slices ->
                val slice = slices[pos] ?: return@withLock
                when (slice.state) {
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

    private fun createSnapshot(chunk: Chunk, pos: IVec3): SliceState.Snapshot {
        // TODO
        return SliceState.Snapshot(
            tiles = airTiles,
        )
    }
}

private fun sliceBounds(pos: IVec3): Aabb {
    val min = DVec3(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble()) * 16.0
    return Aabb(min, min + 16.0)
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
