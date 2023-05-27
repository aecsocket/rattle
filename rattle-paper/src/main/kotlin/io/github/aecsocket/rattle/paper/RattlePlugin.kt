package io.github.aecsocket.rattle.paper

import io.github.aecsocket.alexandria.log.Log
import io.github.aecsocket.alexandria.log.info
import io.github.aecsocket.alexandria.paper.AlexandriaPlugin
import io.github.aecsocket.alexandria.paper.SETTINGS_PATH
import io.github.aecsocket.alexandria.paper.commandManager
import io.github.aecsocket.alexandria.paper.extension.alexandriaPaperSerializers
import io.github.aecsocket.alexandria.paper.extension.forWorld
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.rapier.RapierEngine
import org.bukkit.World
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.ObjectMapper

lateinit var Rattle: RattlePlugin
    private set

class RattlePlugin : AlexandriaPlugin<RattleHook.Settings>(rattleManifest), RattleHook<World> {
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
    override val engine: PhysicsEngine get() = mEngine

    private val mPhysics = HashMap<World, PaperWorldPhysics>()
    val physics: Map<World, PaperWorldPhysics> get() = mPhysics

    init {
        Rattle = this
    }

    override fun loadSettings(node: ConfigurationNode) = node.get() ?: RattleHook.Settings()

    override fun onLoad(log: Log) {
        PaperRattleCommand(this)
        mEngine = RapierEngine(settings.rapier)
        log.info { "Loaded physics engine ${mEngine.name} v${mEngine.version}" }
    }

    override fun onReload(log: Log) {
        mEngine.settings = settings.rapier
    }

    override fun physicsOrNull(world: World) = mPhysics[world]

    override fun physicsOrCreate(world: World) = mPhysics.computeIfAbsent(world) {
        val physics = mEngine.createSpace(settings.worlds.forWorld(world) ?: PhysicsSpace.Settings())
        // TODO
        val terrain = NoOpTerrainStrategy
        val entities = NoOpEntityStrategy
        PaperWorldPhysics(physics, world, terrain, entities)
    }
}
