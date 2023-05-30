package io.github.aecsocket.rattle.paper

import io.github.aecsocket.alexandria.log.Log
import io.github.aecsocket.alexandria.paper.AlexandriaPlugin
import io.github.aecsocket.alexandria.paper.SETTINGS_PATH
import io.github.aecsocket.alexandria.paper.extension.alexandriaPaperSerializers
import io.github.aecsocket.alexandria.paper.extension.forWorld
import io.github.aecsocket.alexandria.paper.extension.registerEvents
import io.github.aecsocket.alexandria.sync.Locked
import io.github.aecsocket.alexandria.sync.Sync
import io.github.aecsocket.glossa.MessageProxy
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.rapier.RapierEngine
import io.github.aecsocket.rattle.stats.timestampedList
import io.github.aecsocket.rattle.world.WorldPhysics
import net.kyori.adventure.key.Key
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.ObjectMapper
import java.util.concurrent.atomic.AtomicBoolean

lateinit var Rattle: RattlePlugin
    private set

class RattlePlugin :
    AlexandriaPlugin<RattleHook.Settings>(rattleManifest),
    RattleHook<World>,
    RattleServer<World, CommandSender>
{
    override val configOptions: ConfigurationOptions = ConfigurationOptions.defaults()
        .serializers { it
            .registerAll(alexandriaPaperSerializers)
            .registerAnnotatedObjects(ObjectMapper.factoryBuilder()
                .addDiscoverer(dataClassFieldDiscoverer())
                .build()
            )
        }

    override val savedResources = listOf(SETTINGS_PATH)
    override val rattle get() = this

    private lateinit var mEngine: RapierEngine
    override val engine: PhysicsEngine
        get() = mEngine
    lateinit var messages: MessageProxy<RattleMessages>
        private set

    private val mPhysics = HashMap<World, Sync<PaperWorldPhysics>>()
    val physics: Map<World, Sync<PaperWorldPhysics>> get() = mPhysics

    private val players = HashMap<Player, PaperRattlePlayer>()

    override val primitiveBodies = PaperPrimitiveBodies(this)
    override val engineTimings = timestampedList<Long>(0)

    private val stepping = AtomicBoolean(false)
    private val executor = RattleServer.createExecutor(this)

    override val worlds: Iterable<World>
        get() = Bukkit.getWorlds()

    init {
        Rattle = this
    }

    override fun onEnable() {
        super.onEnable()
        PaperRattleCommand(this)
        registerEvents(RattleListener(this))
        RattleHook.onInit(this) { mEngine = it }

        scheduling.onServer().runRepeating {
            RattleServer.onTick(
                server = this,
                stepping = stepping,
                beforeStep = { dt ->
                    Bukkit.getPluginManager().callEvent(RattleEvents.BeforePhysicsStep(dt))
                },
                engineTimings = engineTimings,
            )
        }
    }

    override fun onDisable() {
        super.onDisable()
        RattleServer.onDestroy(this, executor)
    }

    override fun loadSettings(node: ConfigurationNode) = node.get() ?: RattleHook.Settings()

    override fun onLoad(log: Log) {
        RattleHook.onLoad(this, { messages = it }, engineTimings)
    }

    override fun onReload(log: Log) {
        RattleHook.onReload(this, mEngine)
    }

    override fun runTask(task: Runnable) {
        executor.submit(task)
    }

    override fun playerData(sender: CommandSender): PaperRattlePlayer? {
        return if (sender is Player) {
            players.computeIfAbsent(sender) { PaperRattlePlayer(this, sender) }
        } else null
    }

    internal fun removePlayerData(player: Player) {
        players -= player
    }

    internal fun removeWorld(world: World) {
        // first remove, then wait for lock, then destroy
        mPhysics.remove(world)?.withLock { (physics) ->
            physics.destroy()
        }
    }

    // adapter

    override fun key(world: World) = world.key()

    override fun physicsOrNull(world: World): Sync<PaperWorldPhysics>? {
        return mPhysics[world]
    }

    override fun physicsOrCreate(world: World): Sync<PaperWorldPhysics> {
        return mPhysics.computeIfAbsent(world) {
            Locked(RattleHook.createWorldPhysics(
                this,
                settings.worlds.forWorld(world),
            ) { space, terrain, entities ->
                PaperWorldPhysics(world, space, terrain, entities)
            })
        }
    }
}
