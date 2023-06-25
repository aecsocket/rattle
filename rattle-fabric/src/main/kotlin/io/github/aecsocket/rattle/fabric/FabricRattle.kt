package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.alexandria.ItemRender
import io.github.aecsocket.alexandria.fabric.AlexandriaMod
import io.github.aecsocket.alexandria.fabric.ItemDisplayRender
import io.github.aecsocket.alexandria.fabric.create
import io.github.aecsocket.alexandria.fabric.extension.forWorld
import io.github.aecsocket.alexandria.fabric.serializer.fabricSerializers
import io.github.aecsocket.alexandria.hook.AlexandriaHook
import io.github.aecsocket.alexandria.sync.Locked
import io.github.aecsocket.alexandria.sync.Sync
import io.github.aecsocket.glossa.Glossa
import io.github.aecsocket.glossa.MessageProxy
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.impl.RattleHook
import io.github.aecsocket.rattle.impl.RattleMessages
import io.github.aecsocket.rattle.impl.rattleManifest
import io.github.aecsocket.rattle.serializer.rattleSerializers
import io.github.oshai.kotlinlogging.KLogger
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents
import net.kyori.adventure.platform.fabric.PlayerLocales
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.ObjectMapper
import java.util.concurrent.locks.ReentrantLock

lateinit var Rattle: FabricRattle
    private set

@Suppress("UnstableApiUsage")
class FabricRattle : AlexandriaMod<RattleHook.Settings>(
    manifest = rattleManifest,
    configOptions = ConfigurationOptions.defaults()
        .serializers { it
            .registerAll(fabricSerializers)
            .registerAll(rattleSerializers)
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

    lateinit var lineItem: ItemStack

    internal val rattle = object : RattleHook() {
        override val ax: AlexandriaHook<*>
            get() = this@FabricRattle.ax

        override val log: KLogger
            get() = this@FabricRattle.log

        override val settings: Settings
            get() = this@FabricRattle.settings

        override val glossa: Glossa
            get() = this@FabricRattle.glossa

        override val draw = object : Draw {
            override fun lineItem(render: ItemRender) {
                (render as ItemDisplayRender).item(lineItem)
            }
        }
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

    override fun onInit() {
        rattle.init()
        FabricRattleCommand(this)
        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            platform = server.rattle()
            log.trace { "Set up Rattle platform" }
        }
        ServerLifecycleEvents.SERVER_STOPPING.register { server ->
            server.rattle().destroy()
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

        EntityTrackingEvents.START_TRACKING.register { trackedEntity, player ->
            player.serverLevel().physicsOrNull()?.withLock { physics ->
                physics.simpleBodies.onTrackEntity(player, trackedEntity)
            }
        }
        EntityTrackingEvents.STOP_TRACKING.register { trackedEntity, player ->
            player.serverLevel().physicsOrNull()?.withLock { physics ->
                physics.simpleBodies.onUntrackEntity(player, trackedEntity)
            }
        }
    }

    override fun onLoad() {
        rattle.load(platform)
        lineItem = settings.draw.lineItem.create()
    }

    override fun onReload() {
        rattle.reload()
    }

    fun physicsOrNull(world: ServerLevel): Sync<FabricWorldPhysics>? =
        (world as LevelPhysicsAccess).rattle_getPhysics()

    fun physicsOrCreate(world: ServerLevel): Sync<FabricWorldPhysics> {
        world as LevelPhysicsAccess
        val physics = world.rattle_getPhysics() ?: run {
            val lock = ReentrantLock()
            val spaceSettings = settings.worldPhysics.forWorld(world) ?: PhysicsSpace.Settings()
            val platform = world.server.rattle()
            val physics = engine.createSpace(spaceSettings)
            physics.lock = lock

            val simpleBodies = FabricSimpleBodies(world, platform, physics, this.settings.simpleBodies)
            val terrain = if (settings.terrain.enabled) {
                FabricDynamicTerrain(world, platform, physics, settings.terrain)
            } else null
            val entities: FabricEntityStrategy? = null // TODO

            Locked(FabricWorldPhysics(world, physics, terrain, entities, simpleBodies), lock).also {
                world.rattle_setPhysics(it)
            }
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

fun ServerLevel.hasPhysics() = physicsOrNull() != null
