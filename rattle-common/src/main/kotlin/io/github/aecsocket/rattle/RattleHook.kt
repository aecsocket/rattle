package io.github.aecsocket.rattle

import io.github.aecsocket.alexandria.BossBarDescriptor
import io.github.aecsocket.alexandria.hook.AlexandriaHook
import io.github.aecsocket.alexandria.hook.AlexandriaManifest
import io.github.aecsocket.alexandria.hook.AlexandriaSettings
import io.github.aecsocket.alexandria.hook.fallbackLocale
import io.github.aecsocket.alexandria.log.info
import io.github.aecsocket.alexandria.log.warn
import io.github.aecsocket.alexandria.sync.Sync
import io.github.aecsocket.glossa.MessageProxy
import io.github.aecsocket.glossa.messageProxy
import io.github.aecsocket.rattle.rapier.RapierEngine
import io.github.aecsocket.rattle.stats.MutableTimestampedList
import io.github.aecsocket.rattle.stats.TimestampedList
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.format.TextColor
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

val rattleManifest = AlexandriaManifest(
    id = "rattle",
    accentColor = TextColor.color(0xdeab14),
    languageResources = listOf(
        "lang/en-US.yml",
    ),
)

interface RattleHook<W> : AlexandriaHook {
    @ConfigSerializable
    data class Settings(
        override val defaultLocale: Locale = fallbackLocale,
        val timeStepMultiplier: Real = 1.0,
        val worlds: Map<String, PhysicsSpace.Settings> = emptyMap(),
        val stats: Stats = Stats(),
        val jobs: Jobs = Jobs(),
        val rapier: RapierEngine.Settings = RapierEngine.Settings(),
    ) : AlexandriaSettings {
        @ConfigSerializable
        data class Stats(
            val timingBuffers: List<Double> = listOf(5.0, 15.0, 60.0),
            val timingBarBuffer: Double = 5.0,
            val timingBar: BossBarDescriptor = BossBarDescriptor(),
        )

        @ConfigSerializable
        data class Jobs(
            val threadTerminateTime: Double = 10.0,
        )
    }

    override val settings: Settings
    val engine: PhysicsEngine

    companion object {
        fun <W> onInit(
            rattle: RattleHook<W>,
            setEngine: (RapierEngine) -> Unit,
        ) {
            val engine = RapierEngine(rattle.settings.rapier)
            setEngine(engine)
            rattle.log.info { "Loaded physics engine ${engine.name} v${engine.version}" }
        }

        fun <W> onLoad(
            rattle: RattleHook<W>,
            setMessages: (MessageProxy<RattleMessages>) -> Unit,
            engineTimings: MutableTimestampedList<Long>?,
        ) {
            setMessages(rattle.glossa.messageProxy())
            engineTimings?.buffer = (rattle.settings.stats.timingBuffers.max() * 1000).toLong()
        }

        fun <W> onReload(
            rattle: RattleHook<W>,
            engine: RapierEngine,
        ) {
            engine.settings = rattle.settings.rapier
        }

        fun <T : WorldPhysics<*>> createWorldPhysics(
            rattle: RattleHook<*>,
            settings: PhysicsSpace.Settings?,
            create: (PhysicsSpace, TerrainStrategy, EntityStrategy) -> T,
        ): T {
            val physics = rattle.engine.createSpace(settings ?: PhysicsSpace.Settings())
            // TODO
            val terrain = NoOpTerrainStrategy
            val entities = NoOpEntityStrategy
            return create(physics, terrain, entities)
        }
    }
}

interface RattleServer<W, C : Audience> {
    val rattle: RattleHook<W>
    val primitiveBodies: PrimitiveBodies<W>
    val engineTimings: TimestampedList<Long>
    val worlds: Iterable<W>

    fun runTask(task: Runnable)

    // adapting types

    fun playerData(sender: C): RattlePlayer<W, *>?

    fun key(world: W): Key

    fun physicsOrNull(world: W): Sync<out WorldPhysics<W>>?

    fun physicsOrCreate(world: W): Sync<out WorldPhysics<W>>

    fun hasPhysics(world: W) = physicsOrNull(world) != null

    companion object {
        fun <W> onDestroy(server: RattleServer<W, *>, executor: ExecutorService) {
            val settings = server.rattle.settings

            executor.shutdown()
            server.rattle.log.info { "Waiting ${settings.jobs.threadTerminateTime} sec for physics jobs" }
            if (!executor.awaitTermination((settings.jobs.threadTerminateTime * 1000).toLong(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow()
                server.rattle.log.warn { "Could not wait for physics jobs" }
            }

            val worlds = server.worlds
            val count = worlds.sumOf { world ->
                val res = server.physicsOrNull(world)?.withLock { physics ->
                    physics.destroy()
                    1
                } ?: 0
                res
            }
            server.rattle.log.info { "Destroyed $count world physics space(s)" }
        }

        fun <W> onTick(
            server: RattleServer<W, *>,
            stepping: AtomicBoolean,
            beforeStep: () -> Unit,
            engineTimings: MutableTimestampedList<Long>,
        ) {
            server.primitiveBodies.onTick()

            server.runTask {
                if (stepping.getAndSet(true)) return@runTask
                val start = System.nanoTime()

                beforeStep()
                server.primitiveBodies.onPhysicsStep()

                // SAFETY: we lock all worlds in a batch at once, so no other thread can access it
                // we batch process all our worlds under lock, then we batch release all locks
                // no other thread should have access to our worlds while we're working
                val locks = server.worlds
                    .mapNotNull { world -> server.physicsOrNull(world) }
                val worlds = locks.map { it.lock() }

                worlds.forEach { it.onPhysicsStep() }
                server.rattle.engine.stepSpaces(
                    0.05 * server.rattle.settings.timeStepMultiplier,
                    worlds.map { it.physics },
                )

                locks.forEach { it.unlock() }

                val end = System.nanoTime()
                engineTimings += (end - start)
                stepping.set(false)
            }
        }
    }
}
