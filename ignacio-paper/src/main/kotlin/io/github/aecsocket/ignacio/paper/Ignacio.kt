package io.github.aecsocket.ignacio.paper

import com.github.retrooper.packetevents.PacketEvents
import io.github.aecsocket.alexandria.Logging
import io.github.aecsocket.alexandria.LoggingList
import io.github.aecsocket.alexandria.paper.AlexandriaPlugin
import io.github.aecsocket.alexandria.paper.fallbackLocale
import io.github.aecsocket.alexandria.paper.seralizer.alexandriaPaperSerializers
import io.github.aecsocket.glossa.MessageProxy
import io.github.aecsocket.glossa.messageProxy
import io.github.aecsocket.ignacio.IgnacioEngine
import io.github.aecsocket.ignacio.jolt.JoltEngine
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder
import io.papermc.paper.util.Tick
import net.kyori.adventure.text.format.TextColor
import org.bukkit.entity.Player
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.ObjectMapper
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private lateinit var instance: Ignacio
val IgnacioAPI get() = instance

private val configOptions: ConfigurationOptions = ConfigurationOptions.defaults()
    .serializers { it
        .registerAll(alexandriaPaperSerializers)
        .registerAnnotatedObjects(ObjectMapper.factoryBuilder()
            .addDiscoverer(dataClassFieldDiscoverer())
            .build()
        )
    }

class Ignacio : AlexandriaPlugin(Manifest("ignacio",
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
        val deltaTimeMultiplier: Float = 1.0f,
        val jolt: JoltEngine.Settings = JoltEngine.Settings(),
    ) : AlexandriaPlugin.Settings {}

    override lateinit var settings: Settings private set
    lateinit var engine: IgnacioEngine private set
    lateinit var messages: MessageProxy<IgnacioMessages> private set
    private var globalDeltaTime = 0f
    private val players = ConcurrentHashMap<Player, IgnacioPlayer>()

    init {
        instance = this
    }

    override fun onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this))
        PacketEvents.getAPI().settings
            .checkForUpdates(false)
            .bStats(true)
        PacketEvents.getAPI().load()

        super.onLoad()

        engine = JoltEngine(settings.jolt)
        logger.info("${ProcessHandle.current().pid()}: Loaded ${engine::class.simpleName} (${engine.build})")
    }

    override fun onEnable() {
        IgnacioCommand(this)
    }

    override fun onDisable() {
        engine.destroy()
    }

    override fun configOptions() = configOptions

    override fun loadSettings(node: ConfigurationNode?) {
        settings = node?.get() ?: Settings()
    }

    override fun load(log: LoggingList) {
        messages = glossa.messageProxy()
        globalDeltaTime = (Tick.of(1).toMillis() / 1000.0f) * settings.deltaTimeMultiplier
    }

    override fun reload(log: Logging) {
        when (val engine = engine) {
            is JoltEngine -> engine.settings = settings.jolt
        }
    }

    fun playerData(player: Player) = players.computeIfAbsent(player) { IgnacioPlayer(this, player) }

    internal fun removePlayer(player: Player) {
        players.remove(player)
    }

    internal fun update() {
        players.toMap().forEach { (_, player) ->
            player.update()
        }
    }
}
