package io.github.aecsocket.rattle.paper

import io.github.aecsocket.alexandria.hook.AlexandriaHook
import io.github.aecsocket.alexandria.hook.AlexandriaManifest
import io.github.aecsocket.alexandria.paper.AlexandriaPlugin
import io.github.aecsocket.alexandria.paper.SETTINGS_PATH
import io.github.aecsocket.alexandria.paper.extension.alexandriaPaperSerializers
import io.github.aecsocket.rattle.PhysicsEngine
import io.github.aecsocket.rattle.RattleHook
import io.github.aecsocket.rattle.rapier.RapierEngine
import net.kyori.adventure.text.format.TextColor
import org.bukkit.World
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.ObjectMapper
import java.util.logging.Logger

lateinit var RattleAPI: Rattle
    private set

class Rattle : AlexandriaPlugin<RattleHook.Settings>(AlexandriaManifest(
    id = "rattle",
    accentColor = TextColor.color(0xdeab14),
    languageResources = listOf(),
    savedResources = listOf(
        SETTINGS_PATH,
    ),
)), AlexandriaHook {
    override val configOptions: ConfigurationOptions = ConfigurationOptions.defaults()
        .serializers { it
            .registerAll(alexandriaPaperSerializers)
            .registerAnnotatedObjects(ObjectMapper.factoryBuilder()
                .addDiscoverer(dataClassFieldDiscoverer())
                .build()
            )
        }

    private lateinit var mEngine: RapierEngine
    val engine: PhysicsEngine get() = mEngine

    private val mPhysics = HashMap<World, WorldPhysics>()
    val physics: Map<World, WorldPhysics> get() = mPhysics

    init {
        RattleAPI = this
    }

    override fun loadSettings(node: ConfigurationNode) = node.get() ?: RattleHook.Settings()

    override fun onLoad(log: Logger) {
        mEngine = RapierEngine(settings.rapier)
        log.info("Loaded physics engine ${mEngine.name} v${mEngine.version}")
    }

    override fun onReload(log: Logger) {
        mEngine.settings = settings.rapier
    }

    fun physicsOrNull(world: World): WorldPhysics? {
        return mPhysics[world]
    }
}
