package io.github.aecsocket.rattle

import io.github.aecsocket.alexandria.BossBarDescriptor
import io.github.aecsocket.alexandria.hook.AlexandriaHook
import io.github.aecsocket.alexandria.hook.AlexandriaManifest
import io.github.aecsocket.alexandria.hook.AlexandriaSettings
import io.github.aecsocket.alexandria.hook.fallbackLocale
import io.github.aecsocket.rattle.rapier.RapierEngine
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

    val engineTimings: TimestampedList<Long>

    companion object {
        fun <T : WorldPhysics<*>> createWorldPhysics(
            hook: RattleHook<*>,
            settings: PhysicsSpace.Settings?,
            create: (PhysicsSpace, TerrainStrategy, EntityStrategy) -> T,
        ): T {
            val physics = hook.engine.createSpace(settings ?: PhysicsSpace.Settings())
            // TODO
            val terrain = NoOpTerrainStrategy
            val entities = NoOpEntityStrategy
            return create(physics, terrain, entities)
        }
    }
}
