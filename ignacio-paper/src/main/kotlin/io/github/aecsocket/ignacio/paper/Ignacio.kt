package io.github.aecsocket.ignacio.paper

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.wrapper.PacketWrapper
import io.github.aecsocket.alexandria.api.paper.AlexandriaApiPlugin
import io.github.aecsocket.alexandria.api.paper.extension.runRepeating
import io.github.aecsocket.alexandria.core.Logging
import io.github.aecsocket.alexandria.core.LoggingList
import io.github.aecsocket.alexandria.core.extension.alexandriaSerializers
import io.github.aecsocket.alexandria.core.extension.registerExact
import io.github.aecsocket.glossa.core.*
import io.github.aecsocket.ignacio.core.IgnacioEngine
import io.github.aecsocket.ignacio.core.PhysicsSpace
import io.github.aecsocket.ignacio.core.math.Vec3dSerializer
import io.github.aecsocket.ignacio.core.math.Vec3fSerializer
import io.github.aecsocket.ignacio.jolt.JoltEngine
import io.github.aecsocket.ignacio.paper.display.StandRenders
import io.github.aecsocket.ignacio.physx.PhysxEngine
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder
import net.kyori.adventure.text.format.TextColor
import org.bukkit.World
import org.bukkit.entity.Player
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.ObjectMapper
import java.util.*

private lateinit var instance: Ignacio
val IgnacioAPI get() = instance

enum class IgnacioEngines {
    NONE,
    JOLT,
    PHYSX,
}

private val configOptions: ConfigurationOptions = ConfigurationOptions.defaults()
    .serializers {
        it.registerAll(alexandriaSerializers)
        it.registerExact(Vec3fSerializer)
        it.registerExact(Vec3dSerializer)
        it.registerAnnotatedObjects(ObjectMapper.factoryBuilder()
            .addDiscoverer(dataClassFieldDiscoverer())
            .build()
        )
    }

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
        override val defaultLocale: Locale = Locale.forLanguageTag("en-US"),
        val engine: IgnacioEngines = IgnacioEngines.NONE,
        val jolt: JoltEngine.Settings = JoltEngine.Settings(),
        val physx: PhysxEngine.Settings = PhysxEngine.Settings(),
    ) : AlexandriaApiPlugin.Settings

    val renders = StandRenders()
    val primitiveBodies = PrimitiveBodies()
    val physicsSpaces = HashMap<World, PhysicsSpace>()

    override lateinit var settings: Settings private set
    lateinit var messages: MessageProxy<IgnacioMessages> private set
    lateinit var engine: IgnacioEngine private set

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

        engine = when (settings.engine) {
            IgnacioEngines.JOLT -> JoltEngine(settings.jolt)
            IgnacioEngines.PHYSX -> PhysxEngine(settings.physx, logger)
            else -> throw RuntimeException("Physics engine is not set")
        }
    }

    override fun onEnable() {
        IgnacioCommand(this)

        runRepeating {
            primitiveBodies.update()
            physicsSpaces.forEach { (_, space) ->
                space.update(0.05f)
            }
        }
    }

    override fun onDisable() {
        physicsSpaces.forEach { (_, space) ->
            engine.destroySpace(space)
        }
        engine.destroy()
    }

    override fun configOptions() = configOptions

    override fun loadSettings(node: ConfigurationNode?) {
        settings = node?.get() ?: Settings()
    }

    override fun load(log: LoggingList) {
        messages = glossa.messageProxy()
    }

    override fun reload(log: Logging) {
        when (val engine = engine) {
            is JoltEngine -> engine.settings = settings.jolt
            is PhysxEngine -> engine.settings = settings.physx
        }
    }

    fun physicsSpaceOf(world: World) = physicsSpaces.computeIfAbsent(world) {
        engine.createSpace(PhysicsSpace.Settings())
    }
}

internal fun Player.sendPacket(packet: PacketWrapper<*>) =
    PacketEvents.getAPI().playerManager.sendPacket(this, packet)
