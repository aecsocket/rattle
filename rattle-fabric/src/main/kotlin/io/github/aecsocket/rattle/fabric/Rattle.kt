package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.alexandria.fabric.AlexandriaMod
import io.github.aecsocket.alexandria.fabric.extension.alexandriaFabricSerializers
import io.github.aecsocket.alexandria.fabric.extension.forLevel
import io.github.aecsocket.alexandria.log.Log
import io.github.aecsocket.alexandria.log.info
import io.github.aecsocket.alexandria.sync.Locked
import io.github.aecsocket.alexandria.sync.Sync
import io.github.aecsocket.glossa.MessageProxy
import io.github.aecsocket.glossa.messageProxy
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.rapier.RapierEngine
import io.github.aecsocket.rattle.stats.timestampedList
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.kyori.adventure.platform.fabric.FabricServerAudiences
import net.kyori.adventure.platform.fabric.PlayerLocales
import net.kyori.adventure.platform.fabric.impl.server.ServerBossBarListener
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.ObjectMapper

object Rattle : AlexandriaMod<RattleHook.Settings>(rattleManifest), RattleHook<ServerLevel> {
    class Adventure(
        val platform: FabricServerAudiences,
        val bossBars: ServerBossBarListener,
    )

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
    override val engine: PhysicsEngine
        get() = mEngine

    lateinit var messages: MessageProxy<RattleMessages>
        private set

    override val primitiveBodies = FabricPrimitiveBodies()
    override val engineTimings = timestampedList<Long>(0)

    var adventure: Adventure? = null

    override fun onInitialize() {
        super.onInitialize()
        RattleHook.onInit(this) { mEngine = it }
        FabricRattleCommand(this)

        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            val platform = FabricServerAudiences.of(server)
            adventure = Adventure(
                platform = platform,
                bossBars = ServerBossBarListener(platform),
            )
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { server ->
            adventure = null
            RattleHook.onServerStop(this, server.allLevels)
        }

        PlayerLocales.CHANGED_EVENT.register { player, newLocale ->
            player.rattle.messages = messages.forLocale(newLocale ?: settings.defaultLocale)
        }

        ServerPlayerEvents.AFTER_RESPAWN.register { oldPlayer, newPlayer, _ ->
            adventure?.bossBars?.replacePlayer(oldPlayer, newPlayer)
        }
    }

    override fun loadSettings(node: ConfigurationNode) = node.get() ?: RattleHook.Settings()

    override fun onLoad(log: Log) {
        RattleHook.onLoad(this, { messages = it }, engineTimings)
    }

    override fun onReload(log: Log) {
        RattleHook.onReload(this, mEngine)
    }

    // adapter

    override fun key(world: ServerLevel) = world.dimension().key()

    override fun physicsOrNull(world: ServerLevel): Sync<FabricWorldPhysics>? {
        world as LevelPhysicsAccess
        return world.rattle_getPhysics()?.let { Locked(it) }
    }

    override fun physicsOrCreate(world: ServerLevel): Sync<FabricWorldPhysics> {
        world as LevelPhysicsAccess
        val physics = world.rattle_getPhysics() ?: run {
            val physics = RattleHook.createWorldPhysics(
                this,
                settings.worlds.forLevel(world),
            ) { space, terrain, entities ->
                FabricWorldPhysics(world, space, terrain, entities)
            }
            world.rattle_setPhysics(physics)
            physics
        }
        return Locked(physics)
    }
}

val ServerPlayer.rattle get() = (this as RattlePlayerAccess).rattle_getData()

fun ServerLevel.physicsOrNull() = Rattle.physicsOrNull(this)

fun ServerLevel.physicsOrCreate() = Rattle.physicsOrCreate(this)
