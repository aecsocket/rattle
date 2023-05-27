package io.github.aecsocket.rattle

import io.github.aecsocket.alexandria.hook.AlexandriaHook
import io.github.aecsocket.alexandria.hook.AlexandriaManifest
import io.github.aecsocket.alexandria.hook.AlexandriaSettings
import io.github.aecsocket.alexandria.hook.fallbackLocale
import io.github.aecsocket.rattle.rapier.RapierEngine
import net.kyori.adventure.text.format.TextColor
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import java.util.*

const val DEFAULT = "default"

val rattleManifest = AlexandriaManifest(
    id = "rattle",
    accentColor = TextColor.color(0xdeab14),
    languageResources = listOf(),
)

interface RattleHook<W> : AlexandriaHook {
    @ConfigSerializable
    data class Settings(
        override val defaultLocale: Locale = fallbackLocale,
        val worlds: Map<String, PhysicsSpace.Settings> = emptyMap(),
        val rapier: RapierEngine.Settings = RapierEngine.Settings(),
    ) : AlexandriaSettings {
        @ConfigSerializable
        data class World(
            val gravity: Vec = Vec(0.0, -9.81, 0.0),
        )
    }

    override val settings: Settings
    val engine: PhysicsEngine

    fun physicsOrNull(world: W): WorldPhysics<W>?

    fun physicsOrCreate(world: W): WorldPhysics<W>
}
