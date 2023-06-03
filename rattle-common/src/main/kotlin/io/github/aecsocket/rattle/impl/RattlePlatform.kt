package io.github.aecsocket.rattle.impl

import io.github.aecsocket.alexandria.sync.Sync
import io.github.aecsocket.rattle.Real
import io.github.aecsocket.rattle.stats.TimestampedList
import io.github.aecsocket.rattle.world.*
import net.kyori.adventure.key.Key
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
                // SAFETY: this isn't safe, but at this point we just need to destroy this world
                // if it's still locked, then we are going to completely screw up the internal state
                // hopefully it won't matter since we're destroying the space and platform anyway
                val physics = physicsOrNull(world)?.leak() ?: return@run 0
                physics.destroy()
                1
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
