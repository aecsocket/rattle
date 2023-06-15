package io.github.aecsocket.rattle.paper

import io.github.aecsocket.alexandria.desc.ParticleShaping
import io.github.aecsocket.alexandria.paper.extension.toColor
import io.github.aecsocket.alexandria.paper.extension.toDVec
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
import org.bukkit.block.Block
import org.spongepowered.configurate.objectmapping.ConfigSerializable

private val airTiles = arrayOfNulls<PaperDynamicTerrain.Tile?>(TILES_IN_SLICE)

private val debugColliderBound = PaperDynamicTerrain.DebugInfo(NamedTextColor.WHITE, 0.0)
private val debugNewSlice = PaperDynamicTerrain.DebugInfo(NamedTextColor.BLUE, 0.2)
private val debugPersistSlice = PaperDynamicTerrain.DebugInfo(NamedTextColor.GRAY, 0.0)
private val debugUpdateSlice = PaperDynamicTerrain.DebugInfo(NamedTextColor.GREEN, 0.4)
private val debugRemoveSlice = PaperDynamicTerrain.DebugInfo(NamedTextColor.RED, 0.2)

class PaperDynamicTerrain(
    private val rattle: PaperRattle,
    val physics: PhysicsSpace,
    private val world: World,
    val settings: Settings = Settings(),
) : TerrainStrategy {
    @ConfigSerializable
    data class Settings(
        val removeIn: Double = 1.0,
        val expandVelocity: Real = 0.1,
        val expandConstant: Real = 1.0,
    )

    data class DebugInfo(
        val color: TextColor,
        val offset: Real,
    )

    sealed interface Layer {
        data class Solid(
            val material: PhysicsMaterial,
        ) : Layer

        data class Fluid(
            val density: Real,
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

    inner class Slice(val pos: IVec3, var state: SliceState) {
        var remove: SliceRemove = SliceRemove.None
        var data: SliceData? = null
            private set

        fun swapData(value: SliceData?) {
            data?.layers?.forEach { collKey ->
                // hours wasted: at least 4
                //physics.colliders.remove(collKey)?.destroy()
            }
            data = value
        }
    }

    sealed interface SliceRemove {
        /* TODO: Kotlin 1.9 data */ object None : SliceRemove

        data class PendingMove(val at: Long) : SliceRemove

        data class PendingDestroy(
            var stepsLeft: Int,
        ) : SliceRemove
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

        fun destroy() {
            map.forEach { (_, slice) ->
                slice.swapData(null)
            }
            map.clear()
            dirty.clear()
        }

        fun all(): Map<IVec3, Slice> = map

        operator fun get(pos: IVec3) = map[pos]

        operator fun contains(pos: IVec3) = map.contains(pos)

        fun add(slice: Slice) {
            if (map.contains(slice.pos))
                throw IllegalArgumentException("Slice already exists at ${slice.pos}")
            map[slice.pos] = slice
        }

        fun remove(pos: IVec3) {
            map.remove(pos)
        }

        fun dirty(pos: IVec3) {
            dirty += pos
        }

        fun clean(): Set<IVec3> {
            return dirty.toSet().also { this.dirty.clear() }
        }
    }

    private val slices = Locked(Slices())
    private val layers: Array<Layer> = arrayOf(
        Layer.Solid(PhysicsMaterial(friction = 0.8, restitution = 0.2)),
        Layer.Fluid(1.0),
    )

    init {
        physics.onCollision { event ->
            if (event.state != PhysicsSpace.OnCollision.State.STOPPED) return@onCollision
            slices.withLock { slices ->
                // TODO optimize, hashSet or something
                slices.all().forEach { (_, slice) ->
                    val sliceColl = slice.data?.layers?.find { it == event.colliderA || it == event.colliderB } ?: return@forEach

                    fun wakeParent(coll: ColliderKey) {
                        physics.colliders.read(coll)!!.parent?.let { physics.rigidBodies.write(it) }?.sleep()
                    }

                    wakeParent(if (sliceColl == event.colliderA) event.colliderA else event.colliderA)
                }
            }
        }
    }

    override fun destroy() {
        slices.withLock { slices ->
            slices.destroy()
        }
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

    fun onUpdate(pos: IVec3) {
        slices.withLock { slices ->
            val slice = slices[pos] ?: return@withLock
            drawDebugAabb(sliceBounds(pos), debugUpdateSlice)
            // reschedule a snapshot if we need to
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

    private fun transformStates(slices: Slices, toRemove: Set<IVec3>) {
        slices.clean().forEach { pos ->
            val slice = slices[pos] ?: return@forEach
            when (val remove = slice.remove) {
                is SliceRemove.None -> {
                    if (toRemove.contains(pos)) {
                        slice.remove = SliceRemove.PendingMove(
                            at = System.currentTimeMillis() + (settings.removeIn * 1000).toLong(),
                        )
                    }
                }
                is SliceRemove.PendingMove -> {
                    slices.dirty(pos)
                    // check that we still want to delete this slice
                    if (toRemove.contains(pos)) {
                        if (System.currentTimeMillis() >= remove.at) {
                            // move colliders
                            slice.data?.layers?.forEach coll@ { collKey ->
                                val coll = physics.colliders.write(collKey) ?: return@coll
                                coll.position(Iso(Vec(0.0, 10000.0, 0.0))) // todo
                            }
                            slice.remove = SliceRemove.PendingDestroy(
                                stepsLeft = 5,
                            )
                        }
                    } else {
                        slice.remove = SliceRemove.None
                    }
                }
                is SliceRemove.PendingDestroy -> {
                    remove.stepsLeft -= 1
                    if (remove.stepsLeft <= 0) {
                        // then delete them
                        slices.remove(pos)
                        slice.swapData(null)
                        slice.remove = SliceRemove.None
                    }
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
        val layerShapes = Array<MutableList<Compound.Child>>(layers.size) { ArrayList() }

        // assuming we're processing a slice at (1, 1, 1)...
        //  - collider at (16, 16, 16)
        //  - compound children:
        //    - stone block at (1, 1, 1)
        //      - compound child box, half extents (0.5), at (1.5, 1.5, 1.5)

        state.tiles.forEachIndexed { i, tile ->
            tile ?: return@forEachIndexed
            val localPos = posInChunk(i).run { Vec(x.toReal(), y.toReal(), z.toReal()) } + 0.5
            val layer = layerShapes[tile.layerId]

            val shapes = tile.shapes.map { child ->
                Compound.Child(
                    shape = child.shape,
                    delta = child.delta * Iso(localPos),
                )
            }
            layer += shapes
        }

        return SliceData(
            layers = layerShapes.mapIndexedNotNull { i, shapes ->
                if (shapes.isEmpty()) return@mapIndexedNotNull null
                val layer = layers[i]

                val coll = rattle.engine.createCollider(
                    shape = rattle.engine.createShape(Compound(shapes)),
                    position = StartPosition.Absolute(
                        Iso((slice.pos * 16).run { Vec(x.toReal(), y.toReal(), z.toReal()) }),
                    )
                )
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
        //if (true) return // todo
        /*
        rs: Starting step
rs: 1
rs: 2
rs: 4
rs: 5
rs: 6
rs: 7
rs: 8
rs: 9
rs: 11
rs: 12
rs: 13
rs: 14
rs: 15
rs: 16
rs: 17
rs: 22
rs: 23
rs: 24
rs: 25
rs: 26
rs: 27

            println!("rs: 26");
            if let Some(queries) = query_pipeline.as_deref_mut() {
                println!("rs: 27"); <==
                queries.update_incremental(
                    colliders,
                    &modified_colliders,
                    &[],
                    remaining_substeps == 0,
                );
            }

            rs: Starting step

rs: Starting step
rs: 1
rs: 2
rs: 4
rs: 5
rs: 6
rs: 7
rs: 8
rs: 9
rs: 11
rs: 12
rs: Q 1
rs: Q 3
rs: Q 5
rs: Q finish
rs: 13
rs: 14
rs: 15
rs: 16
rs: 17
rs: 22
rs: 23
rs: 24
rs: 25
rs: 26
rs: 27
rs: Q 1
rs: Q 3
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 4
rs: Q 4A
rs: Q 5
rs: Q 6
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
rs: Q 6A
thread '<unnamed>' panicked at 'No element at index', /home/socket/Projects/rapier/crates/rapier3d-f64/../../src/pipeline/query_pipeline.rs:357:17
stack backtrace:
   0:     0x7f88fd79066a - std::backtrace_rs::backtrace::libunwind::trace::h1ac6254167c780d9
                               at /rustc/22f247c6f3ed388cb702d01c2ff27da658a8b353/library/std/src/../../backtrace/src/backtrace/libunwind.rs:93:5
   1:     0x7f88fd79066a - std::backtrace_rs::backtrace::trace_unsynchronized::hec2af85915e24f36
                               at /rustc/22f247c6f3ed388cb702d01c2ff27da658a8b353/library/std/src/../../backtrace/src/backtrace/mod.rs:66:5
   2:     0x7f88fd79066a - std::sys_common::backtrace::_print_fmt::h58a4e3535fcce206
                               at /rustc/22f247c6f3ed388cb702d01c2ff27da658a8b353/library/std/src/sys_common/backtrace.rs:65:5
   3:     0x7f88fd79066a - <std::sys_common::backtrace::_print::DisplayBacktrace as core::fmt::Display>::fmt::h5107e13758b8321c
                               at /rustc/22f247c6f3ed388cb702d01c2ff27da658a8b353/library/std/src/sys_common/backtrace.rs:44:22
   4:     0x7f88fd7aefae - core::fmt::write::h2e851dc027730d81
                               at /rustc/22f247c6f3ed388cb702d01c2ff27da658a8b353/library/core/src/fmt/mod.rs:1232:17
   5:     0x7f88fd78e2d5 - std::io::Write::write_fmt::hca00074de9f85084
                               at /rustc/22f247c6f3ed388cb702d01c2ff27da658a8b353/library/std/src/io/mod.rs:1684:15
   6:     0x7f88fd790435 - std::sys_common::backtrace::_print::h870053c845cddf24
                               at /rustc/22f247c6f3ed388cb702d01c2ff27da658a8b353/library/std/src/sys_common/backtrace.rs:47:5
   7:     0x7f88fd790435 - std::sys_common::backtrace::print::hb56add862f96c5fd
                               at /rustc/22f247c6f3ed388cb702d01c2ff27da658a8b353/library/std/src/sys_common/backtrace.rs:34:9
   8:     0x7f88fd791bef - std::panicking::default_hook::{{closure}}::h636d4ba3ff8fdc46
                               at /rustc/22f247c6f3ed388cb702d01c2ff27da658a8b353/library/std/src/panicking.rs:271:22
   9:     0x7f88fd79192b - std::panicking::default_hook::hf29b58145ee6e43c
                               at /rustc/22f247c6f3ed388cb702d01c2ff27da658a8b353/library/std/src/panicking.rs:290:9
  10:     0x7f88fd792198 - std::panicking::rust_panic_with_hook::hbf9ef936d990c16f
                               at /rustc/22f247c6f3ed388cb702d01c2ff27da658a8b353/library/std/src/panicking.rs:692:13
  11:     0x7f88fd792099 - std::panicking::begin_panic_handler::{{closure}}::h6be6433dcb901f4b
                               at /rustc/22f247c6f3ed388cb702d01c2ff27da658a8b353/library/std/src/panicking.rs:583:13
  12:     0x7f88fd790ad6 - std::sys_common::backtrace::__rust_end_short_backtrace::h802b6104a4d80829
                               at /rustc/22f247c6f3ed388cb702d01c2ff27da658a8b353/library/std/src/sys_common/backtrace.rs:150:18
  13:     0x7f88fd791da2 - rust_begin_unwind
                               at /rustc/22f247c6f3ed388cb702d01c2ff27da658a8b353/library/std/src/panicking.rs:579:5
  14:     0x7f88fd624403 - core::panicking::panic_fmt::hf7a8a88b9669732e
                               at /rustc/22f247c6f3ed388cb702d01c2ff27da658a8b353/library/core/src/panicking.rs:64:14
  15:     0x7f88fd7ad9c1 - core::panicking::panic_display::ha14ecfa86c697326
                               at /rustc/22f247c6f3ed388cb702d01c2ff27da658a8b353/library/core/src/panicking.rs:147:5
  16:     0x7f88fd7ad96b - core::panicking::panic_str::h0f5ad79b66e15d80
                               at /rustc/22f247c6f3ed388cb702d01c2ff27da658a8b353/library/core/src/panicking.rs:131:5
  17:     0x7f88fd6243c6 - core::option::expect_failed::h6e9ae5a1d735f1ff
                               at /rustc/22f247c6f3ed388cb702d01c2ff27da658a8b353/library/core/src/option.rs:2091:5
  18:     0x7f88fd6d322a - rapier3d_f64::pipeline::query_pipeline::QueryPipeline::update_incremental::hc039ebe670ceef43
  19:     0x7f88fd6d04b8 - rapier3d_f64::pipeline::physics_pipeline::PhysicsPipeline::step::h073110eac85393fc
  20:     0x7f88fd630796 - RprPhysicsPipeline_step_all
  21:     0x7f89802930a5 - <unknown>
fatal runtime error: failed to initiate panic, error 5

        if refit_and_rebalance {
            println!("rs: Q 6");
            let _ = self.qbvh.refit(0.0, &mut self.workspace, |handle| {
                println!("rs: Q 6A = {:?}", handle);
                colliders[*handle].compute_aabb()
            });
            println!("rs: Q 7");
            self.qbvh.rebalance(0.0, &mut self.workspace);
        }

rs: Q 6A = ColliderHandle(Index { index: 63, generation: 85 })
 .. Isometry { rotation: [0.023060505281168774, -0.07631822832763699, -0.04456479926016141, 0.9958201242132081], translation: [9.087271181911872, 79.27585386552853, -0.3226061464469934] }
rs: Q 6A = ColliderHandle(Index { index: 109, generation: 92 })
 .. Isometry { rotation: [0.0, 0.0, 0.0, 1.0], translation: [10.118152678902737, 145.69035141642212, 9.156744383001415] }
rs: Q 6A = ColliderHandle(Index { index: 84, generation: 85 })
 .. Isometry { rotation: [0.16869145213288023, 0.1690841319730785, -0.6864359930891825, -0.6868474195073302], translation: [15.043540813670306, 71.59831337204204, -19.69024832725183] }
rs: Q 6A = ColliderHandle(Index { index: 96, generation: 85 })
 .. Isometry { rotation: [-0.0002551283153670157, 0.004839132411981812, 0.0007248032307390261, 0.9999879961116127], translation: [8.895699718688075, 76.59736254297809, -4.687474116659055] }
rs: Q 6A = ColliderHandle(Index { index: 63, generation: 85 })
 .. Isometry { rotation: [0.023060505281168774, -0.07631822832763699, -0.04456479926016141, 0.9958201242132081], translation: [9.087271181911872, 79.27585386552853, -0.3226061464469934] }

repeated...

         */

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
                        // if `snapshot` is null, it means that slice had no data (it may do in the future though)
                        // in which case we just mark it as "built" and have it hold no colliders
                        slice.state = snapshot ?: SliceState.Built
                        slices.dirty(pos)
                    }
                    else -> {
                        // silently fail
                    }
                }
            }
        }
    }

    private fun createSnapshot(chunk: Chunk, pos: IVec3): SliceState.Snapshot? {
        // TODO if chunk is empty, we don't snapshot
        if (pos.y < -world.minHeight / 16 || pos.y >= world.maxHeight / 16) {
            return null
        }

        val tiles: Array<out Tile?> = Array(TILES_IN_SLICE) { i ->
            val (lx, ly, lz) = posInChunk(i)
            val gy = pos.y * 16 + ly
            // guaranteed to be in range because of the Y check at the start
            val block = chunk.getBlock(lx, gy, lz)
            wrapBlock(block)
        }

        return SliceState.Snapshot(
            tiles = tiles,
        )
    }

    private fun wrapBlock(block: Block): Tile? {
        if (block.isPassable) {
            return null
        }

        val shapes = block.collisionShape.boundingBoxes
            .map { box ->
                Compound.Child(
                    shape = boxShape(box.max.subtract(box.min).toDVec() / 2.0),
                    delta = Iso(box.center.toDVec() - 0.5),
                )
            }

        return Tile(
            layerId = 0, // todo
            shapes = shapes,
        )
    }

    private fun boxShape(halfExtent: Vec): Shape {
        // todo cache this?
        return rattle.engine.createShape(Box(halfExtent))
    }
}

private fun sliceBounds(pos: IVec3): Aabb {
    val min = DVec3(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble()) * 16.0
    return Aabb(min, min + 16.0)
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
