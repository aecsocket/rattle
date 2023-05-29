package io.github.aecsocket.rattle.paper

import io.github.aecsocket.alexandria.log.Log
import io.github.aecsocket.alexandria.log.info
import io.github.aecsocket.alexandria.paper.AlexandriaPlugin
import io.github.aecsocket.alexandria.paper.SETTINGS_PATH
import io.github.aecsocket.alexandria.paper.extension.alexandriaPaperSerializers
import io.github.aecsocket.alexandria.paper.extension.forWorld
import io.github.aecsocket.glossa.MessageProxy
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.rapier.RapierEngine
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.ObjectMapper
import java.util.concurrent.atomic.AtomicBoolean

lateinit var Rattle: RattlePlugin
    private set

class RattlePlugin :
    AlexandriaPlugin<RattleHook.Settings>(rattleManifest),
    RattleHook<World>,
    RattleServer<World, CommandSender> {
    override val configOptions: ConfigurationOptions = ConfigurationOptions.defaults()
        .serializers { it
            .registerAll(alexandriaPaperSerializers)
            .registerAnnotatedObjects(ObjectMapper.factoryBuilder()
                .addDiscoverer(dataClassFieldDiscoverer())
                .build()
            )
        }

    override val savedResources = listOf(SETTINGS_PATH)

    private lateinit var mEngine: RapierEngine
    override val engine: PhysicsEngine
        get() = mEngine
    lateinit var messages: MessageProxy<RattleMessages>
        private set

    private val mPhysics = HashMap<World, PaperWorldPhysics>()
    val physics: Map<World, PaperWorldPhysics> get() = mPhysics

    private val stepping = AtomicBoolean(false)

    init {
        Rattle = this
    }

    override fun onEnable() {
        super.onEnable()
        PaperRattleCommand(this)
        RattleHook.onInit(this) { mEngine = it }

        scheduling.onServer().runRepeating {
            RattleServer.onTick(this, stepping, {  }, engineTimings)
        }
    }

    override fun loadSettings(node: ConfigurationNode) = node.get() ?: RattleHook.Settings()

    override fun onLoad(log: Log) {
        RattleHook.onLoad(this, { messages = it }, )
    }

    override fun onReload(log: Log) {
        RattleHook.onReload(this, mEngine)
    }
}
