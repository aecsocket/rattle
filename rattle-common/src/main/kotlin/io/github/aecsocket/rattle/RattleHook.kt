package io.github.aecsocket.rattle

import io.github.aecsocket.alexandria.BossBarDescriptor
import io.github.aecsocket.alexandria.hook.AlexandriaHook
import io.github.aecsocket.alexandria.hook.AlexandriaManifest
import io.github.aecsocket.alexandria.hook.AlexandriaSettings
import io.github.aecsocket.alexandria.hook.fallbackLocale
import io.github.aecsocket.alexandria.log.Log
import io.github.aecsocket.alexandria.log.info
import io.github.aecsocket.glossa.MessageProxy
import io.github.aecsocket.glossa.messageProxy
import io.github.aecsocket.rattle.rapier.RapierEngine
import io.github.aecsocket.rattle.stats.MutableTimestampedList
import io.github.aecsocket.rattle.stats.TimestampedList
import net.kyori.adventure.text.format.TextColor
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import java.util.*

val rattleManifest = AlexandriaManifest(
    id = "rattle",
    accentColor = TextColor.color(0xdeab14),
    languageResources = listOf(
        "lang/en-US.yml",
    ),
)

interface RattleHook<W> : AlexandriaHook, RattleAdapter<W> {
    @ConfigSerializable
    data class Settings(
        override val defaultLocale: Locale = fallbackLocale,
        val timeStepMultiplier: Real = 1.0,
        val worlds: Map<String, PhysicsSpace.Settings> = emptyMap(),
        val stats: Stats = Stats(),
        val rapier: RapierEngine.Settings = RapierEngine.Settings(),
    ) : AlexandriaSettings {
        @ConfigSerializable
        data class Stats(
            val timingBuffers: List<Double> = listOf(5.0, 15.0, 60.0),
            val timingBarBuffer: Double = 5.0,
            val timingBar: BossBarDescriptor = BossBarDescriptor(),
        )
    }

    override val settings: Settings
    val engine: PhysicsEngine
    val primitiveBodies: PrimitiveBodies<W>
    val engineTimings: TimestampedList<Long>

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
            engineTimings: MutableTimestampedList<Long>,
        ) {
            setMessages(rattle.glossa.messageProxy())
            engineTimings.buffer = (rattle.settings.stats.timingBuffers.max() * 1000).toLong()
        }

        fun <W> onReload(
            rattle: RattleHook<W>,
            engine: RapierEngine,
        ) {
            engine.settings = rattle.settings.rapier
        }

        fun <W> onServerStop(rattle: RattleHook<W>, worlds: Iterable<W>) {
            val destroyed = worlds.sumOf { world ->
                val res = rattle.physicsOrNull(world)?.withLock {
                    it.destroy()
                    1
                } ?: 0
               // weird pattern because kotlin gets confused on if the result is an Int or a Long otherwise
               res
            }
            rattle.log.info { "Destroyed $destroyed world physics space(s)" }
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
