package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.alexandria.fabric.AlexandriaMod
import io.github.aecsocket.alexandria.fabric.extension.alexandriaFabricSerializers
import io.github.aecsocket.alexandria.fabric.extension.forLevel
import io.github.aecsocket.alexandria.log.Log
import io.github.aecsocket.alexandria.log.info
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.rapier.RapierEngine
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.ObjectMapper
import java.util.concurrent.atomic.AtomicBoolean

object Rattle : AlexandriaMod<RattleHook.Settings>(rattleManifest), RattleHook {
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

    private val mPhysics = HashMap<ResourceKey<Level>, FabricWorldPhysics>()
    val physics: Map<ResourceKey<Level>, FabricWorldPhysics> get() = mPhysics

    internal val stepping = AtomicBoolean(false)

    override fun onLoad(log: Log) {
        FabricRattleCommand(this)
        mEngine = RapierEngine(settings.rapier)
        log.info { "Loaded physics engine ${mEngine.name} v${mEngine.version}" }
    }

    override fun onReload(log: Log) {
        mEngine.settings = settings.rapier
    }

    override fun loadSettings(node: ConfigurationNode) = node.get() ?: RattleHook.Settings()

    override val worlds = Worlds()
    class Worlds : RattleHook.Worlds {
        operator fun contains(world: Level) = mPhysics.contains(world.dimension())
        override fun contains(world: World) = contains(unwrap(world))

        operator fun get(world: Level) = mPhysics[world.dimension()]
        override fun get(world: World) = get(unwrap(world))

        fun getOrCreate(world: Level) = mPhysics.computeIfAbsent(world.dimension()) {
            RattleHook.createWorldPhysics(
                Rattle,
                settings.worlds.forLevel(world)
            ) { physics, terrain, entities ->
                FabricWorldPhysics(world, physics, terrain, entities)
            }
        }
        override fun getOrCreate(world: World) = getOrCreate(unwrap(world))

        fun destroy(world: Level) {
            val data = mPhysics.remove(world.dimension()) ?: return
            data.physics.destroy()
        }
        override fun destroy(world: World) = destroy(unwrap(world))
    }
}
