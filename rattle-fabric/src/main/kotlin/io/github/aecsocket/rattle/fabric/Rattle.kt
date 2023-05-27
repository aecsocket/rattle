package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.alexandria.fabric.AlexandriaMod
import io.github.aecsocket.alexandria.fabric.extension.alexandriaFabricSerializers
import io.github.aecsocket.alexandria.fabric.extension.forLevel
import io.github.aecsocket.alexandria.log.Log
import io.github.aecsocket.alexandria.log.info
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.rapier.RapierEngine
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.ObjectMapper

object Rattle : AlexandriaMod<RattleHook.Settings>(rattleManifest), RattleHook<ServerLevel> {
    override val configOptions: ConfigurationOptions = ConfigurationOptions.defaults()
        .serializers { it
            .registerAll(alexandriaFabricSerializers)
            .registerAnnotatedObjects(
                ObjectMapper.factoryBuilder()
                .addDiscoverer(dataClassFieldDiscoverer())
                .build()
            )
        }

    private lateinit var mEngine: RapierEngine
    override val engine: PhysicsEngine get() = mEngine

    private val mWorlds = HashMap<ResourceKey<Level>, FabricWorldPhysics>()
    val worlds: Map<ResourceKey<Level>, FabricWorldPhysics> get() = mWorlds

    override fun onLoad(log: Log) {
        RattleCommand(this, commandManager())
        mEngine = RapierEngine(settings.rapier)
        log.info { "Loaded physics engine ${mEngine.name} v${mEngine.version}" }
    }

    override fun onReload(log: Log) {
        mEngine.settings = settings.rapier
    }

    override fun loadSettings(node: ConfigurationNode) = node.get() ?: RattleHook.Settings()

    override fun physicsOrNull(world: ServerLevel) = mWorlds[world.dimension()]

    override fun physicsOrCreate(world: ServerLevel) = mWorlds.computeIfAbsent(world.dimension()) {
        val physics = mEngine.createSpace(settings.worlds.forLevel(world) ?: PhysicsSpace.Settings())
        // TODO
        val terrain = NoOpTerrainStrategy
        val entities = NoOpEntityStrategy
        FabricWorldPhysics(physics, world, terrain, entities)
    }
}
