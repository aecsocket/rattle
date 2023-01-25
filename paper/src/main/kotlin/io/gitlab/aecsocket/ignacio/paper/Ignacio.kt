package io.gitlab.aecsocket.ignacio.paper

import io.gitlab.aecsocket.ignacio.bullet.BltBackend
import io.gitlab.aecsocket.ignacio.core.*
import io.gitlab.aecsocket.ignacio.physx.PhxBackend
import org.bukkit.Bukkit
import org.bukkit.World
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
private const val BACKEND = "backend"

private lateinit var instance: Ignacio
val IgnacioAPI get() = instance

enum class IgnacioDefaultBackend(val key: String) {
    BULLET  ("bullet"),
    PHYSX   ("physx")
}

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
        val space: IgSpaceSettings = IgSpaceSettings()
    )

    private val _spaces = HashMap<UUID, IgPhysicsSpace>()
    val spaces: Map<UUID, IgPhysicsSpace> get() = _spaces

    lateinit var backend: IgnacioBackend private set
    lateinit var settings: Settings private set

    init {
        instance = this
    }

    private fun loadSettingsNode() = HoconConfigurationLoader.builder()
        .defaultOptions(configOptions)
        .file(dataFolder.resolve(PATH_SETTINGS))
        .build().load()

    override fun onLoad() {
        val node = try {
            val node = loadSettingsNode()
            settings = node.igForce()
            node
        } catch (ex: Exception) {
            throw RuntimeException("Could not load settings", ex)
        }

        val backendType = node.node(BACKEND).igForce<IgnacioDefaultBackend>()
        val backendNode = node.node(backendType.key)
        backend = when (backendType) {
            IgnacioDefaultBackend.BULLET -> BltBackend(dataFolder, backendNode.igForce(), logger)
            IgnacioDefaultBackend.PHYSX -> PhxBackend(dataFolder, backendNode.igForce(), logger)
        }
    }

    override fun onEnable() {
        IgnacioCommand(this)
        Bukkit.getPluginManager().registerEvents(IgnacioEventListener(this), this)
    }

    fun reload() {
        settings = try {
            loadSettingsNode().get { Settings() }
        } catch (ex: Exception) {
            logger.severe("Could not load settings")
            ex.printStackTrace()
            Settings()
        }
    }

    fun spaceOf(world: World) = _spaces.computeIfAbsent(world.uid) {
        backend.createSpace(settings.space)
    }

    fun removeSpace(world: World) {
        _spaces.remove(world.uid)?.let { space ->
            space.destroy()
        }
    }
}
