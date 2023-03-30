package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.alexandria.paper.AlexandriaApiPlugin
import io.github.aecsocket.alexandria.paper.fallbackLocale
import net.kyori.adventure.text.format.TextColor
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import java.util.*

private lateinit var instance: Ignacio
val IgnacioAPI get() = instance

class Ignacio : AlexandriaApiPlugin(Manifest("ignacio",
    accentColor = TextColor.color(0xdeab14),
    languageResources = listOf(
        "lang/en-US.yml",
    ),
    savedResources = listOf(
        "settings.yml",
    ),
)) {
    companion object {
        @JvmStatic
        fun api() = IgnacioAPI
    }

    @ConfigSerializable
    data class Settings(
        override val defaultLocale: Locale = fallbackLocale,

    ) : AlexandriaApiPlugin.Settings {}

    override lateinit var settings: Settings private set

    init {
        instance = this
    }

    override fun onLoad() {
    }

    override fun onEnable() {
        IgnacioCommand(this)
    }

    override fun loadSettings(node: ConfigurationNode?) {
        settings = node?.get() ?: Settings()
    }
}
