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
        val rapier: RapierEngine.Settings = RapierEngine.Settings(),
    ) : AlexandriaSettings

    override val settings: Settings
    val engine: PhysicsEngine
}
