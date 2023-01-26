package io.gitlab.aecsocket.ignacio.paper

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.wrapper.PacketWrapper
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder
import io.gitlab.aecsocket.ignacio.bullet.BulletBackend
import io.gitlab.aecsocket.ignacio.core.*
import io.gitlab.aecsocket.ignacio.core.math.Vec3
import io.gitlab.aecsocket.ignacio.core.math.Vec3Serializer
import io.gitlab.aecsocket.ignacio.physx.PhysxBackend
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.ObjectMapper
import org.spongepowered.configurate.util.NamingSchemes
import java.util.UUID

private const val PATH_SETTINGS = "settings.conf"

private lateinit var instance: Ignacio
val IgnacioAPI get() = instance

enum class IgBackends { BULLET, PHYSX, NONE }

private val configOptions = ConfigurationOptions.defaults()
    .serializers {
        it.registerExact(Vec3::class.java, Vec3Serializer)
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
        val backend: IgBackends = IgBackends.NONE,
        val bullet: BulletBackend.Settings = BulletBackend.Settings(),
        val physx: PhysxBackend.Settings = PhysxBackend.Settings(),
        val space: IgPhysicsSpace.Settings = IgPhysicsSpace.Settings()
    )

    val physicsThread = IgPhysicsThread(logger)
    val meshes = Meshes()
    private val _spaces = HashMap<UUID, IgPhysicsSpace>()
    val spaces: Map<UUID, IgPhysicsSpace> get() = _spaces

    lateinit var backend: IgBackend<*> private set
    lateinit var settings: Settings private set

    init {
        instance = this
    }

    private fun loadSettingsNode() = HoconConfigurationLoader.builder()
        .defaultOptions(configOptions)
        .file(dataFolder.resolve(PATH_SETTINGS))
        .build().load()

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
            settings = node.igForce()
        } catch (ex: Exception) {
            throw RuntimeException("Could not load settings", ex)
        }

        backend = when (settings.backend) {
            IgBackends.BULLET -> BulletBackend(settings.bullet, dataFolder, logger)
            IgBackends.PHYSX -> PhysxBackend(settings.physx, logger)
            else -> throw RuntimeException("Ignacio has not been set up with a backend - specify `backend` in $PATH_SETTINGS")
        }
    }

    override fun onEnable() {
        PacketEvents.getAPI().init()
        IgnacioCommand(this)
        Bukkit.getPluginManager().registerEvents(IgnacioEventListener(this), this)
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, {
            meshes.update()
            _spaces.forEach { (_, space) ->
                physicsThread.execute {
                    space.step()
                }
            }
        }, 0, 1)
        physicsThread.start()
    }

    override fun onDisable() {
        PacketEvents.getAPI().terminate()
        backend.destroy()
        physicsThread.destroy()
    }

    fun reload() {
        settings = try {
            loadSettingsNode().get { Settings() }
        } catch (ex: Exception) {
            logger.severe("Could not load settings")
            ex.printStackTrace()
            Settings()
        }

        when (val backend = backend) {
            is BulletBackend -> backend.reload(settings.bullet)
            is PhysxBackend -> backend.reload(settings.physx)
        }
    }

    fun spaceOf(world: World) = _spaces.computeIfAbsent(world.uid) {
        backend.createSpace(settings.space)
    }

    fun removeSpace(world: World) {
        _spaces.remove(world.uid)?.let { space ->
            backend.destroySpace(space)
        }
    }
}

internal fun Player.sendPacket(packet: PacketWrapper<*>) =
    PacketEvents.getAPI().playerManager.sendPacket(this, packet)
