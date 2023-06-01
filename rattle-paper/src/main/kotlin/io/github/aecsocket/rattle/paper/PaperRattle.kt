package io.github.aecsocket.rattle.paper

import io.github.aecsocket.alexandria.hook.AlexandriaHook
import io.github.aecsocket.alexandria.log.Log
import io.github.aecsocket.alexandria.paper.AlexandriaPlugin
import io.github.aecsocket.alexandria.paper.extension.alexandriaPaperSerializers
import io.github.aecsocket.alexandria.paper.extension.forWorld
import io.github.aecsocket.alexandria.paper.extension.registerEvents
import io.github.aecsocket.alexandria.sync.Locked
import io.github.aecsocket.alexandria.sync.Sync
import io.github.aecsocket.glossa.Glossa
import io.github.aecsocket.glossa.MessageProxy
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.impl.RattleHook
import io.github.aecsocket.rattle.impl.rattleManifest
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.WorldUnloadEvent
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.ObjectMapper

lateinit var Rattle: PaperRattle
    private set

class PaperRattle : AlexandriaPlugin<RattleHook.Settings>(
    manifest = rattleManifest,
    configOptions = ConfigurationOptions.defaults()
        .serializers { it
            .registerAll(alexandriaPaperSerializers)
            .registerAnnotatedObjects(ObjectMapper.factoryBuilder()
                .addDiscoverer(dataClassFieldDiscoverer())
                .build()
            )
        },
    savedResources = listOf()
) {
    companion object {
        @JvmStatic
        fun api() = Rattle
    }

    internal val rattle = object : RattleHook() {
        override val ax: AlexandriaHook<*>
            get() = this@PaperRattle.ax

        override val log: Log
            get() = this@PaperRattle.log

        override val settings: Settings
            get() = this@PaperRattle.settings

        override val glossa: Glossa
            get() = this@PaperRattle.glossa
    }

    val engine: PhysicsEngine
        get() = rattle.engine

    val messages: MessageProxy<RattleMessages>
        get() = rattle.messages

    fun runTask(task: Runnable) =
        rattle.runTask(task)

    lateinit var platform: PaperRattlePlatform
        private set

    internal val mWorlds = HashMap<World, Sync<PaperWorldPhysics>>()
    val worlds: Map<World, Sync<PaperWorldPhysics>>
        get() = mWorlds

    private val players = HashMap<Player, PaperRattlePlayer>()

    init {
        Rattle = this
    }

    override fun loadSettings(node: ConfigurationNode) = node.get() ?: RattleHook.Settings()

    override fun onPreInit(log: Log) {
        platform = PaperRattlePlatform(this)
    }

    override fun onInit(log: Log) {
        rattle.init(log)
    }

    override fun onEnable() {
        PaperRattleCommand(this)
        scheduling.onServer().runRepeating {
            players.forEach { (_, player) ->
                player.tick()
            }
            platform.tick()
        }
        registerEvents(object : Listener {
            @EventHandler
            fun on(event: WorldUnloadEvent) {
                mWorlds[event.world]?.withLock { it.destroy() }
            }

            @EventHandler
            fun on(event: PlayerQuitEvent) {
                players.remove(event.player)
            }
        })
    }

    override fun onLoad(log: Log) {
        rattle.load(log, platform)
    }

    override fun onReload(log: Log) {
        rattle.reload(log)
    }

    override fun onDestroy(log: Log) {
        rattle.destroy(log, platform)
    }

    fun physicsOrNull(world: World): Sync<PaperWorldPhysics>? =
        mWorlds[world]

    fun physicsOrCreate(world: World): Sync<PaperWorldPhysics> =
        mWorlds.computeIfAbsent(world) {
            Locked(platform.createWorldPhysics(
                settings.worlds.forWorld(world),
            ) { physics, terrain, entities ->
                PaperWorldPhysics(
                    this,
                    world,
                    physics,
                    terrain,
                    entities,
                    PaperSimpleBodies(world, physics, this),
                )
            })
        }

    fun playerData(player: Player) = players.computeIfAbsent(player) {
        PaperRattlePlayer(this, player)
    }
}

fun World.physicsOrNull() = Rattle.physicsOrNull(this)

fun World.physicsOrCreate() = Rattle.physicsOrCreate(this)

fun World.hasPhysics() = physicsOrNull() != null
