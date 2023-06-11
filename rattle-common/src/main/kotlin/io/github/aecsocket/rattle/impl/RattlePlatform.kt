package io.github.aecsocket.rattle.impl

import io.github.aecsocket.alexandria.sync.Sync
import io.github.aecsocket.rattle.Real
import io.github.aecsocket.rattle.stats.TimestampedList
import io.github.aecsocket.rattle.world.*
import net.kyori.adventure.key.Key
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

abstract class RattlePlatform<W, C>(
    val rattle: RattleHook,
) {
    abstract val worlds: Iterable<W>

    protected abstract fun callBeforeStep(dt: Real)

    abstract fun asPlayer(sender: C): RattlePlayer<W, *>?

    abstract fun key(world: W): Key

    abstract fun physicsOrNull(world: W): Sync<out WorldPhysics<W>>?

    abstract fun physicsOrCreate(world: W): Sync<out WorldPhysics<W>>

    fun hasPhysics(world: W) = physicsOrNull(world) != null

    private val stepping = AtomicBoolean(false)
    val isStepping: Boolean
        get() = stepping.get()

    private val mEngineTimings = TimestampedList<Long>(timingsBufferSize())
    val engineTimings: TimestampedList<Long>
        get() = mEngineTimings

    private fun timingsBufferSize() = (rattle.settings.stats.timingBuffers.max() * 1000).toLong()

    fun load() {
        mEngineTimings.buffer = timingsBufferSize()
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

    fun tick() {
        rattle.runTask {
            if (stepping.getAndSet(true)) return@runTask
            val start = System.nanoTime()
            try {
                val dt = 0.05 * rattle.settings.timeStepMultiplier
                callBeforeStep(dt)

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
                mEngineTimings += (end - start)
                stepping.set(false)
            }
        }
    }
}
