package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.alexandria.fabric.AlexandriaMod
import io.github.aecsocket.alexandria.fabric.extension.alexandriaFabricSerializers
import io.github.aecsocket.alexandria.fabric.extension.forLevel
import io.github.aecsocket.alexandria.fabric.render.DisplayRenders
import io.github.aecsocket.alexandria.hook.AlexandriaHook
import io.github.aecsocket.alexandria.log.Log
import io.github.aecsocket.alexandria.log.trace
import io.github.aecsocket.alexandria.sync.Locked
import io.github.aecsocket.alexandria.sync.Sync
import io.github.aecsocket.glossa.Glossa
import io.github.aecsocket.glossa.MessageProxy
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.impl.RattleHook
import io.github.aecsocket.rattle.impl.RattlePlatform
import io.github.aecsocket.rattle.impl.rattleManifest
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

lateinit var Rattle: FabricRattle
    private set

@Suppress("UnstableApiUsage")
class FabricRattle : AlexandriaMod<RattleHook.Settings>(
    manifest = rattleManifest,
    configOptions = ConfigurationOptions.defaults()
        .serializers { it
            .registerAll(alexandriaFabricSerializers)
            .registerAnnotatedObjects(ObjectMapper.factoryBuilder()
                .addDiscoverer(dataClassFieldDiscoverer())
                .build()
            )
        },
) {
    companion object {
        @JvmStatic
        fun api() = Rattle
    }

    internal val rattle = object : RattleHook() {
        override val ax: AlexandriaHook<*>
            get() = this@FabricRattle.ax

        override val log: Log
            get() = this@FabricRattle.log

        override val settings: Settings
            get() = this@FabricRattle.settings

        override val glossa: Glossa
            get() = this@FabricRattle.glossa
    }

    val engine: PhysicsEngine
        get() = rattle.engine

    val messages: MessageProxy<RattleMessages>
        get() = rattle.messages

    fun runTask(task: Runnable) =
        rattle.runTask(task)

    var platform: FabricRattlePlatform? = null

    init {
        Rattle = this
    }

    override fun loadSettings(node: ConfigurationNode) = node.get() ?: RattleHook.Settings()

    override fun onInit(log: Log) {
        rattle.init(log)
        FabricRattleCommand(this)
        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            platform = server.rattle()
            log.trace { "Set up Rattle platform" }
        }
        ServerLifecycleEvents.SERVER_STOPPING.register { server ->
            server.rattle().destroy(log)
            platform = null
            log.trace { "Tore down Rattle platform" }
        }
        PlayerLocales.CHANGED_EVENT.register { player, newLocale ->
            player.rattle().messages = messages.forLocale(newLocale ?: settings.defaultLocale)
            log.trace { "Updated locale for ${player.name} to $newLocale" }
        }
        ServerPlayerEvents.AFTER_RESPAWN.register { oldPlayer, newPlayer, _ ->
            newPlayer.server.rattle().bossBars.replacePlayer(oldPlayer, newPlayer)
            log.trace { "Replaced player instance for ${newPlayer.name}" }
        }
    }

    override fun onLoad(log: Log) {
        rattle.load(log, platform)
    }

    override fun onReload(log: Log) {
        rattle.reload(log)
    }

    fun physicsOrNull(world: ServerLevel): Sync<FabricWorldPhysics>? =
        (world as LevelPhysicsAccess).rattle_getPhysics()

    fun physicsOrCreate(world: ServerLevel): Sync<FabricWorldPhysics> {
        world as LevelPhysicsAccess
        val physics = world.rattle_getPhysics() ?: run {
            val platform = world.server.rattle()
            val physics = Locked(platform.createWorldPhysics(
                settings.worlds.forLevel(world),
            ) { physics, terrain, entities ->
                FabricWorldPhysics(world, physics, terrain, entities, FabricSimpleBodies(world, platform))
            })
            world.rattle_setPhysics(physics)
            physics
        }
        return physics
    }

    fun playerData(player: ServerPlayer): FabricRattlePlayer =
        (player as PlayerRattleAccess).rattle()
}

fun MinecraftServer.rattle() = (this as ServerRattleAccess).rattle()

fun ServerPlayer.rattle() = (this as PlayerRattleAccess).rattle()

fun ServerLevel.physicsOrNull() = server.rattle().physicsOrNull(this)

fun ServerLevel.physicsOrCreate() = server.rattle().physicsOrCreate(this)
