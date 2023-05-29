package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.alexandria.fabric.AlexandriaMod
import io.github.aecsocket.alexandria.fabric.extension.alexandriaFabricSerializers
import io.github.aecsocket.alexandria.fabric.extension.forLevel
import io.github.aecsocket.alexandria.log.Log
import io.github.aecsocket.alexandria.log.info
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.rapier.RapierEngine
import io.github.aecsocket.rattle.stats.TimestampedList
import io.github.aecsocket.rattle.stats.timestampedList
import net.minecraft.world.level.Level
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.ObjectMapper

object Rattle : AlexandriaMod<RattleHook.Settings>(rattleManifest), RattleHook<Level> {
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

    override val engineTimings = timestampedList<Long>(0)

    override fun onInitialize() {
        super.onInitialize()
        FabricRattleCommand(this)
        mEngine = RapierEngine(settings.rapier)
        log.info { "Loaded physics engine ${mEngine.name} v${mEngine.version}" }
    }

    override fun onLoad(log: Log) {
        engineTimings.buffer = (settings.stats.timingBuffers.max() * 1000).toLong()
    }

    override fun onReload(log: Log) {
        mEngine.settings = settings.rapier
    }

    override fun loadSettings(node: ConfigurationNode) = node.get() ?: RattleHook.Settings()

    override fun key(world: Level) = world.dimension().key()

    override fun physicsOrNull(world: Level): FabricWorldPhysics? {
        world as LevelPhysicsAccess
        return world.rattle_getPhysics()
    }

    override fun physicsOrCreate(world: Level): FabricWorldPhysics {
        world as LevelPhysicsAccess
        world.rattle_getPhysics()?.let { return it }
        val physics = RattleHook.createWorldPhysics(
            this,
            settings.worlds.forLevel(world),
        ) { space, terrain, entities ->
            FabricWorldPhysics(world, space, terrain, entities)
        }
        world.rattle_setPhysics(physics)
        return physics
    }

    override fun destroyPhysics(world: Level) {
        world as LevelPhysicsAccess
        val physics = world.rattle_getPhysics() ?: return
        physics.destroy()
    }
}

fun Level.physicsOrNull() = Rattle.physicsOrNull(this)

fun Level.physicsOrCreate() = Rattle.physicsOrCreate(this)

fun Level.destroyPhysics() = Rattle.destroyPhysics(this)
