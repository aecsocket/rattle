package io.gitlab.aecsocket.ignacio.paper

import io.gitlab.aecsocket.ignacio.bullet.BulletBackend
import io.gitlab.aecsocket.ignacio.core.IgnacioBackend
import io.gitlab.aecsocket.ignacio.physx.PhysxBackend
import org.bukkit.plugin.java.JavaPlugin
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.ObjectMapper
import org.spongepowered.configurate.util.NamingSchemes

private const val PATH_SETTINGS = "settings.conf"
private const val BACKEND = "backend"
private const val BULLET = "bullet"
private const val PHYSX = "physx"

private lateinit var instance: Ignacio
val IgnacioAPI get() = instance

enum class IgnacioDefaultBackend {
    BULLET,
    PHYSX
}

private val configOptions = ConfigurationOptions.defaults()
    .serializers {
        it.registerAnnotatedObjects(ObjectMapper.factoryBuilder()
            .addDiscoverer(dataClassFieldDiscoverer())
            .defaultNamingScheme(NamingSchemes.SNAKE_CASE)
            .build()
        )
    }

class Ignacio : JavaPlugin() {
    @ConfigSerializable
    data class Settings(
        val a: Boolean = false
    )

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
            settings = node.force()
            node
        } catch (ex: Exception) {
            throw RuntimeException("Could not load settings", ex)
        }

        backend = when (node.node(BACKEND).force<IgnacioDefaultBackend>()) {
            IgnacioDefaultBackend.BULLET -> BulletBackend(dataFolder, node.node(BULLET).force(), logger)
            IgnacioDefaultBackend.PHYSX -> PhysxBackend(dataFolder, node.node(PHYSX).force(), logger)
        }
    }

    override fun onEnable() {
        super.onEnable()
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
}
