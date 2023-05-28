package io.github.aecsocket.rattle

import io.github.aecsocket.alexandria.hook.AlexandriaHook
import io.github.aecsocket.alexandria.hook.AlexandriaManifest
import io.github.aecsocket.alexandria.hook.AlexandriaSettings
import io.github.aecsocket.alexandria.hook.fallbackLocale
import io.github.aecsocket.rattle.rapier.RapierEngine
import net.kyori.adventure.text.format.TextColor
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import java.util.*

val rattleManifest = AlexandriaManifest(
    id = "rattle",
    accentColor = TextColor.color(0xdeab14),
    languageResources = listOf(),
)

interface RattleHook : AlexandriaHook {
    @ConfigSerializable
    data class Settings(
        override val defaultLocale: Locale = fallbackLocale,
        val timeStepMultiplier: Real = 1.0,
        val worlds: Map<String, PhysicsSpace.Settings> = emptyMap(),
        val rapier: RapierEngine.Settings = RapierEngine.Settings(),
    ) : AlexandriaSettings

    override val settings: Settings
    val engine: PhysicsEngine

    val worlds: Worlds
    interface Worlds {
        operator fun contains(world: World): Boolean

        operator fun get(world: World): WorldPhysics?

        fun getOrCreate(world: World): WorldPhysics

        fun destroy(world: World)
    }

    companion object {
        fun <T : WorldPhysics> createWorldPhysics(
            hook: RattleHook,
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
