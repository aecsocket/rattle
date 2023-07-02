package io.github.aecsocket.rattle.impl

import io.github.aecsocket.alexandria.EventDispatch
import io.github.aecsocket.alexandria.sync.Sync
import io.github.aecsocket.rattle.CommandSource
import io.github.aecsocket.rattle.World
import io.github.aecsocket.rattle.stats.TimestampedList
import io.github.aecsocket.rattle.world.*
import net.kyori.adventure.key.Key
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A container for a single server's physics state, providing access to world physics.
 */
abstract class RattlePlatform(
    val rattle: RattleHook,
) {
    data class OnPreStep(
        val dt: Double,
    )

    abstract val worlds: Iterable<World>

    private val _onPreStep = EventDispatch<OnPreStep>()
    val onPreStep: EventDispatch<OnPreStep>
        get() = _onPreStep

    abstract fun key(world: World): Key

    abstract fun physicsOrNull(world: World): Sync<out WorldPhysics>?

    abstract fun physicsOrCreate(world: World): Sync<out WorldPhysics>

    fun hasPhysics(world: World) = physicsOrNull(world) != null

    abstract fun asPlayer(sender: CommandSource): RattlePlayer?

    abstract fun setPlayerDraw(player: RattlePlayer, draw: RattlePlayer.Draw?)

    private val stepping = AtomicBoolean(false)
    val isStepping: Boolean
        get() = stepping.get()

    private val _engineTimings = TimestampedList<Long>(timingsBufferSize())
    val engineTimings: TimestampedList<Long>
        get() = _engineTimings

    private fun timingsBufferSize() = (rattle.settings.stats.timingBuffers.max() * 1000).toLong()

    fun load() {
        _engineTimings.buffer = timingsBufferSize()
    }

    fun destroy() {
        val count = worlds.sumOf { world ->
            val res: Int = run {
                // SAFETY: if some part of the program has screwed up, we will never be able to acquire this lock
                // however we don't really care about this space anymore; we just want to attempt to clean it up.
                // If we cannot acquire a lock in time, we cannot destroy the object, therefore we leak memory.
                // However, this is better than the alternative if we ignored the lock and destroyed the world
                // immediately: if the native code is stuck somewhere, it has a high chance of just crashing
                // the entire JVM, possibly before other world state has been saved.
                // And anyway, on a server environment, we don't care.
                physicsOrNull(world)?.let { lock ->
                    lock.tryLock(
                        time = (rattle.settings.jobs.spaceTerminateTime * 1000).toLong(),
                        unit = TimeUnit.MILLISECONDS,
                    )?.let { physics ->
                        physics.destroy()
                        1
                    } ?: run {
                        rattle.log.warn { "Could not acquire lock for physics space of $world - space not destroyed, memory leaked!" }
                        null
                    }
                } ?: 0
            }
            // weird syntax because Kotlin gets confused between sumOf((T) -> Int) and ((T) -> Long)
            res
        }
        rattle.log.info { "Destroyed $count world physics spaces" }
    }

    open fun onTick() {
        rattle.runTask {
            if (stepping.getAndSet(true)) return@runTask
            val start = System.nanoTime()
            try {
                val dt = 0.05 * rattle.settings.timeStepMultiplier
                _onPreStep.dispatch(OnPreStep(dt = dt))

                // SAFETY: we lock all worlds in a batch at once, so no other thread can access it
                // we batch process all our worlds under lock, then we batch release all locks
                // no other thread should have access to our worlds while we're working
                val locks = worlds.mapNotNull { world -> physicsOrNull(world) }
                val worlds = locks.map { it.lock() }

                worlds.forEach { it.onPhysicsStep() }

                rattle.engine.stepSpaces(dt, worlds.map { it.physics })

                locks.forEach { it.unlock() }
                // end lock scope
            } finally {
                val end = System.nanoTime()
                _engineTimings += (end - start)
                stepping.set(false)
            }
        }
    }
}
