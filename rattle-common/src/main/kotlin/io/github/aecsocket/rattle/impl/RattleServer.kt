package io.github.aecsocket.rattle.impl

import io.github.aecsocket.alexandria.log.Log
import io.github.aecsocket.alexandria.log.info
import io.github.aecsocket.alexandria.sync.Sync
import io.github.aecsocket.rattle.AbstractPrimitiveBodies
import io.github.aecsocket.rattle.PhysicsSpace
import io.github.aecsocket.rattle.Real
import io.github.aecsocket.rattle.stats.TimestampedList
import io.github.aecsocket.rattle.stats.timestampedList
import io.github.aecsocket.rattle.world.*
import net.kyori.adventure.key.Key
import java.util.concurrent.atomic.AtomicBoolean

abstract class RattleServer<W, C> {
    abstract val rattle: RattleHook
    abstract val worlds: Iterable<W>
    abstract val primitiveBodies: AbstractPrimitiveBodies<W>

    protected abstract fun callBeforeStep(dt: Real)

    abstract fun asPlayer(sender: C): RattlePlayer<W, *>?

    abstract fun key(world: W): Key

    abstract fun physicsOrNull(world: W): Sync<out WorldPhysics<W>>?

    abstract fun physicsOrCreate(world: W): Sync<out WorldPhysics<W>>

    fun hasPhysics(world: W) = physicsOrNull(world) != null

    private val stepping = AtomicBoolean(false)
    val isStepping: Boolean
        get() = stepping.get()

    private val mEngineTimings = timestampedList<Long>(0)
    val engineTimings: TimestampedList<Long>
        get() = mEngineTimings

    fun load() {
        mEngineTimings.buffer = (rattle.settings.stats.timingBuffers.max() * 1000).toLong()
    }

    fun destroy(log: Log) {
        val count = worlds.sumOf { world ->
            val res = physicsOrNull(world)?.withLock { physics ->
                physics.destroy()
                1
            } ?: 0
            // weird syntax because Kotlin gets confused between sumOf<Int> and <Long>
            res
        }
        log.info { "Destroyed $count world physics spaces" }
    }

    fun tick() {
        rattle.runTask {
            if (stepping.getAndSet(true)) return@runTask
            val start = System.nanoTime()

            val dt = 0.05 * rattle.settings.timeStepMultiplier
            callBeforeStep(dt)
            primitiveBodies.onPhysicsStep()

            // SAFETY: we lock all worlds in a batch at once, so no other thread can access it
            // we batch process all our worlds under lock, then we batch release all locks
            // no other thread should have access to our worlds while we're working
            val locks = worlds.mapNotNull { world -> physicsOrNull(world) }
            val worlds = locks.map { it.lock() }

            worlds.forEach { it.onPhysicsStep() }
            rattle.engine.stepSpaces(dt, worlds.map { it.physics })

            locks.forEach { it.unlock() }
            // end lock scope

            val end = System.nanoTime()
            mEngineTimings += (end - start)
            stepping.set(false)
        }
    }

    fun <T : WorldPhysics<W>> createWorldPhysics(
        spaceSettings: PhysicsSpace.Settings?,
        create: (PhysicsSpace, TerrainStrategy, EntityStrategy) -> T,
    ): T {
        val physics = rattle.engine.createSpace(spaceSettings ?: PhysicsSpace.Settings())
        // TODO
        val terrain = NoOpTerrainStrategy
        val entities = NoOpEntityStrategy
        return create(physics, terrain, entities)
    }
}
