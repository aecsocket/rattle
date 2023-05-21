package io.github.aecsocket.rattle

import io.github.aecsocket.alexandria.hook.AlexandriaHook
import io.github.aecsocket.alexandria.hook.AlexandriaSettings
import io.github.aecsocket.alexandria.hook.fallbackLocale
import io.github.aecsocket.rattle.rapier.RapierEngine
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import java.util.*

interface RattleHook : AlexandriaHook {
    @ConfigSerializable
    data class Settings(
        override val defaultLocale: Locale = fallbackLocale,
        val rapier: RapierEngine.Settings = RapierEngine.Settings(),
    ) : AlexandriaSettings

    override val settings: Settings
    val engine: PhysicsEngine
}
