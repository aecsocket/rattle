package io.github.aecsocket.ignacio.paper

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.wrapper.PacketWrapper
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder
import io.github.aecsocket.ignacio.core.BoxGeometry
import io.github.aecsocket.ignacio.core.IgnacioEngine
import io.github.aecsocket.ignacio.core.PhysicsSpace
import io.github.aecsocket.ignacio.core.math.*
import io.github.aecsocket.ignacio.core.util.force
import io.github.aecsocket.ignacio.jolt.JoltEngine
import io.github.aecsocket.ignacio.paper.util.runRepeating
import io.github.aecsocket.ignacio.physx.PhysxEngine
import net.kyori.adventure.serializer.configurate4.ConfigurateComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.ObjectMapper
import org.spongepowered.configurate.serialize.TypeSerializer
import org.spongepowered.configurate.serialize.TypeSerializerCollection
import org.spongepowered.configurate.util.NamingSchemes
import java.lang.Exception

private lateinit var instance: Ignacio
val IgnacioAPI get() = instance

enum class IgnacioEngines {
    NONE,
    JOLT,
    PHYSX,
}

private const val PATH_SETTINGS = "settings.conf"

private inline fun <reified T> TypeSerializerCollection.Builder.registerExact(serializer: TypeSerializer<T>) =
    registerExact(T::class.java, serializer)

private val configOptions = ConfigurationOptions.defaults()
    .serializers {
        it.registerAll(ConfigurateComponentSerializer.configurate().serializers())
        it.registerExact(Vec3fSerializer)
        it.registerExact(Vec3dSerializer)
        it.registerAnnotatedObjects(ObjectMapper.factoryBuilder()
            .addDiscoverer(dataClassFieldDiscoverer())
            .defaultNamingScheme(NamingSchemes.SNAKE_CASE)
            .build()
        )
    }

class Ignacio : JavaPlugin() {
    companion object {
        @JvmStatic
        fun instance() = IgnacioAPI
    }

    @ConfigSerializable
    data class Settings(
        val engine: IgnacioEngines = IgnacioEngines.NONE,
        val jolt: JoltEngine.Settings = JoltEngine.Settings(),
        val physx: PhysxEngine.Settings = PhysxEngine.Settings(),
    )

    val meshes = StandMeshes()
    val physicsSpaces = HashMap<World, PhysicsSpace>()

    lateinit var settings: Settings private set
    lateinit var engine: IgnacioEngine private set

    init {
        instance = this
    }

    private fun loadSettingsNode() = HoconConfigurationLoader.builder()
        .defaultOptions(configOptions)
        .file(dataFolder.resolve(PATH_SETTINGS))
        .build().load()

    private fun loadInternal() {

    }

    override fun onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this))
        PacketEvents.getAPI().settings
            .checkForUpdates(false)
            .bStats(true)
        PacketEvents.getAPI().load()

        if (!dataFolder.exists()) {
            saveResource(PATH_SETTINGS, false)
        }

        try {
            val node = loadSettingsNode()
            settings = node.force()
        } catch (ex: Exception) {
            throw RuntimeException("Could not load settings", ex)
        }

        engine = when (settings.engine) {
            IgnacioEngines.JOLT -> JoltEngine(settings.jolt)
            IgnacioEngines.PHYSX -> PhysxEngine(settings.physx, logger)
            IgnacioEngines.NONE ->
                throw RuntimeException("Ignacio has not been set up with an engine - specify `engine` in $PATH_SETTINGS")
        }
        logger.info("${ProcessHandle.current().pid()}: Loaded ${engine::class.simpleName} ${engine.build}")

        loadInternal()
    }

    override fun onEnable() {
        IgnacioCommand(this)

        runRepeating {
            meshes.update()
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

    fun reload() {
        settings = try {
            loadSettingsNode().get { Settings() }
        } catch (ex: Exception) {
            logger.severe("Could not load settings")
            ex.printStackTrace()
            Settings()
        }

        when (val engine = engine) {
            is JoltEngine -> engine.settings = settings.jolt
            is PhysxEngine -> engine.settings = settings.physx
        }

        loadInternal()
    }

    fun physicsSpaceOf(world: World) = physicsSpaces.computeIfAbsent(world) {
        engine.createSpace(PhysicsSpace.Settings()).also {
//            it.addStaticBody(
//                BoxGeometry(Vec3f(30_000_000f, 0.5f, 30_000_000f)),
//                Transform(Vec3d(0.0, 64.0, 0.0))
//            )
        }
    }
}

internal fun Player.sendPacket(packet: PacketWrapper<*>) =
    PacketEvents.getAPI().playerManager.sendPacket(this, packet)
