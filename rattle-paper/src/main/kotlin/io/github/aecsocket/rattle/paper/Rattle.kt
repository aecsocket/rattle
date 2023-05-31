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
import io.github.aecsocket.rattle.impl.RattleServer
import io.github.aecsocket.rattle.impl.rattleManifest
import io.github.aecsocket.rattle.stats.TimestampedList
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
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

    internal val rattleServer = object : RattleServer<World, CommandSender>() {
        override val rattle: RattleHook
            get(): RattleHook = this@PaperRattle.rattle

        override val worlds: Iterable<World>
            get() = Bukkit.getWorlds()

        override val primitiveBodies = PaperPrimitiveBodies()

        override fun callBeforeStep(dt: Real) {
            RattleEvents.BeforePhysicsStep(dt).callEvent()
        }

        override fun asPlayer(sender: CommandSender) =
            (sender as? Player)?.let { playerData(it) }

        override fun key(world: World) = world.key()

        override fun physicsOrNull(world: World) =
            this@PaperRattle.physicsOrNull(world)

        override fun physicsOrCreate(world: World) =
            this@PaperRattle.physicsOrCreate(world)
    }

    val isStepping: Boolean
        get() = rattleServer.isStepping

    val engineTimings: TimestampedList<Long>
        get() = rattleServer.engineTimings

    private val mWorlds = HashMap<World, Sync<PaperWorldPhysics>>()
    val worlds: Map<World, Sync<PaperWorldPhysics>>
        get() = mWorlds

    private val players = HashMap<Player, PaperRattlePlayer>()

    init {
        Rattle = this
    }

    override fun loadSettings(node: ConfigurationNode) = node.get() ?: RattleHook.Settings()

    override fun onInit(log: Log) {
        rattle.init(log)
        PaperRattleCommand(this)
        registerEvents(RattleListener(this))
        scheduling.onServer().runRepeating { rattleServer.tick() }
    }

    override fun onLoad(log: Log) {
        rattle.load(log, rattleServer)
    }

    override fun onReload(log: Log) {
        rattle.reload(log)
    }

    override fun onDestroy(log: Log) {
        rattle.destroy(log, rattleServer)
    }

    fun physicsOrNull(world: World): Sync<PaperWorldPhysics>? =
        mWorlds[world]

    fun physicsOrCreate(world: World): Sync<PaperWorldPhysics> =
        mWorlds.computeIfAbsent(world) {
            Locked(rattleServer.createWorldPhysics(
                settings.worlds.forWorld(world)
            ) { physics, terrain, entities ->
                PaperWorldPhysics(world, physics, terrain, entities)
            })
        }

    internal fun destroyWorld(world: World) {
        val physics = mWorlds.remove(world) ?: return
        physics.withLock { it.destroy() }
    }

    fun playerData(player: Player) = players.computeIfAbsent(player) {
        PaperRattlePlayer(this, player)
    }

    internal fun destroyPlayerData(player: Player) {
        players.remove(player)
    }
}

fun World.physicsOrNull() = Rattle.physicsOrNull(this)

fun World.physicsOrCreate() = Rattle.physicsOrCreate(this)

fun World.hasPhysics() = physicsOrNull() != null
