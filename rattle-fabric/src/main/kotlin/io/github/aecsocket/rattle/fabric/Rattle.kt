package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.alexandria.fabric.AlexandriaMod
import io.github.aecsocket.alexandria.fabric.extension.alexandriaFabricSerializers
import io.github.aecsocket.alexandria.fabric.extension.forLevel
import io.github.aecsocket.alexandria.fabric.render.DisplayRenders
import io.github.aecsocket.alexandria.log.Log
import io.github.aecsocket.alexandria.sync.Locked
import io.github.aecsocket.alexandria.sync.Sync
import io.github.aecsocket.glossa.MessageProxy
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.rapier.RapierEngine
import io.github.aecsocket.rattle.stats.timestampedList
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.kyori.adventure.platform.fabric.FabricServerAudiences
import net.kyori.adventure.platform.fabric.PlayerLocales
import net.kyori.adventure.platform.fabric.impl.server.ServerBossBarListener
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.ObjectMapper
import java.util.concurrent.atomic.AtomicBoolean

lateinit var Rattle: RattleMod
    private set

@Suppress("UnstableApiUsage")
class RattleMod : AlexandriaMod<RattleHook.Settings>(rattleManifest), RattleHook<ServerLevel> {
    companion object {
        @JvmStatic
        fun api() = Rattle
    }

    class Server(
        override val rattle: RattleMod,
        private val server: MinecraftServer,
    ) : RattleServer<ServerLevel, CommandSourceStack> {
        val adventure = FabricServerAudiences.of(server)
        val bossBars = ServerBossBarListener(adventure)
        val renders = DisplayRenders()

        override val primitiveBodies = FabricPrimitiveBodies(rattle)
        override val engineTimings = timestampedList<Long>(0)

        private val stepping = AtomicBoolean(false)
        private val executor = RattleServer.createExecutor(rattle)

        override val worlds: Iterable<ServerLevel>
            get() = server.allLevels

        override fun runTask(task: Runnable) {
            executor.submit(task)
        }

        override fun playerData(sender: CommandSourceStack) = (sender.entity as? ServerPlayer)?.rattle()

        override fun key(world: ServerLevel) = world.dimension().key()

        override fun physicsOrNull(world: ServerLevel): Sync<FabricWorldPhysics>? {
            world as LevelPhysicsAccess
            return world.rattle_getPhysics()
        }

        override fun physicsOrCreate(world: ServerLevel): Sync<FabricWorldPhysics> {
            world as LevelPhysicsAccess
            val physics = world.rattle_getPhysics() ?: run {
                val physics = Locked(RattleHook.createWorldPhysics(
                    Rattle,
                    rattle.settings.worlds.forLevel(world),
                ) { space, terrain, entities ->
                    FabricWorldPhysics(world, space, terrain, entities)
                })
                world.rattle_setPhysics(physics)
                physics
            }
            return physics
        }

        fun onTick() {
            RattleServer.onTick(
                server = this,
                stepping = stepping,
                beforeStep = { dt ->
                    RattleEvents.BEFORE_STEP.invoker().beforeStep(this, dt)
                },
                engineTimings = engineTimings,
            )
        }

        fun onDestroy() {
            RattleServer.onDestroy(this, executor)
        }
    }

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

    var server: Server? = null

    init {
        Rattle = this
    }

    override fun onInitialize() {
        super.onInitialize()
        FabricRattleCommand(this)
        RattleHook.onInit(
            rattle = this,
            setEngine = { mEngine = it },
        )

        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            this.server = server.rattle()
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { server ->
            server.rattle().onDestroy()
        }

        PlayerLocales.CHANGED_EVENT.register { player, newLocale ->
            player.rattle().messages = messages.forLocale(newLocale ?: settings.defaultLocale)
        }

        ServerPlayerEvents.AFTER_RESPAWN.register { oldPlayer, newPlayer, _ ->
            newPlayer.server.rattle().bossBars.replacePlayer(oldPlayer, newPlayer)
        }
    }

    override fun loadSettings(node: ConfigurationNode) = node.get() ?: RattleHook.Settings()

    override fun onLoad(log: Log) {
        RattleHook.onLoad(this, { messages = it }, server?.engineTimings)
    }

    override fun onReload(log: Log) {
        RattleHook.onReload(this, mEngine)
    }
}

fun MinecraftServer.rattle() = (this as RattleServerAccess).rattle_getData()

fun ServerPlayer.rattle() = (this as RattlePlayerAccess).rattle_getData()

fun ServerLevel.physicsOrNull() = server.rattle().physicsOrNull(this)

fun ServerLevel.physicsOrCreate() = server.rattle().physicsOrCreate(this)
