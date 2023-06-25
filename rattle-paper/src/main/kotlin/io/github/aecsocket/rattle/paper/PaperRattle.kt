package io.github.aecsocket.rattle.paper

import io.github.aecsocket.alexandria.ItemRender
import io.github.aecsocket.alexandria.extension.registerExact
import io.github.aecsocket.alexandria.hook.AlexandriaHook
import io.github.aecsocket.alexandria.paper.AlexandriaPlugin
import io.github.aecsocket.alexandria.paper.ItemDisplayRender
import io.github.aecsocket.alexandria.paper.create
import io.github.aecsocket.alexandria.paper.extension.forWorld
import io.github.aecsocket.alexandria.paper.extension.position
import io.github.aecsocket.alexandria.paper.extension.registerEvents
import io.github.aecsocket.alexandria.paper.seralizer.paperSerializers
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
import io.papermc.paper.event.packet.PlayerChunkLoadEvent
import io.papermc.paper.event.packet.PlayerChunkUnloadEvent
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.WorldUnloadEvent
import org.bukkit.inventory.ItemStack
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.ObjectMapper
import java.util.concurrent.locks.ReentrantLock

lateinit var Rattle: PaperRattle
    private set

class PaperRattle : AlexandriaPlugin<RattleHook.Settings>(
    manifest = rattleManifest,
    configOptions = ConfigurationOptions.defaults()
        .serializers { it
            .registerAll(paperSerializers)
            .registerAll(rattleSerializers)
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

    lateinit var lineItem: ItemStack

    internal val rattle = object : RattleHook() {
        override val ax: AlexandriaHook<*>
            get() = this@PaperRattle.ax

        override val log: KLogger
            get() = this@PaperRattle.log

        override val settings: Settings
            get() = this@PaperRattle.settings

        override val glossa: Glossa
            get() = this@PaperRattle.glossa

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

    override fun onPreInit() {
        platform = PaperRattlePlatform(this)
    }

    override fun onInit() {
        rattle.init()
    }

    override fun onPostEnable() {
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

            @EventHandler
            fun on(event: PlayerInteractEvent) {
                if (event.action.isLeftClick) {
                    players[event.player]?.onClick()
                }
            }

            // terrain

            private fun terrain(world: World, fn: PaperDynamicTerrain.() -> Unit) {
                runTask {
                    physicsOrNull(world)?.withLock { physics ->
                        physics.terrain?.let(fn)
                    }
                }
            }

            @EventHandler
            fun on(event: PlayerChunkLoadEvent) {
                terrain(event.world) { onTrackChunk(event.player, event.chunk) }
            }

            @EventHandler
            fun on(event: PlayerChunkUnloadEvent) {
                terrain(event.world) { onUntrackChunk(event.player, event.chunk) }
            }

            private fun terrainUpdate(block: Block) {
                terrain(block.world) { onSliceUpdate(block.position().map { it shr 4 }) }
            }

            @EventHandler
            fun on(event: BlockBreakEvent) {
                terrainUpdate(event.block)
            }

            @EventHandler
            fun on(event: BlockPlaceEvent) {
                terrainUpdate(event.block)
            }
        })
    }

    override fun onLoadData() {
        rattle.load(platform)
        lineItem = settings.draw.lineItem.create()
    }

    override fun onReloadData() {
        rattle.reload()
    }

    override fun onDestroy() {
        rattle.destroy(platform)
    }

    fun physicsOrNull(world: World): Sync<PaperWorldPhysics>? =
        mWorlds[world]

    fun physicsOrCreate(world: World): Sync<PaperWorldPhysics> =
        mWorlds.computeIfAbsent(world) {
            val lock = ReentrantLock()
            val spaceSettings = settings.worldPhysics.forWorld(world) ?: PhysicsSpace.Settings()
            val physics = engine.createSpace(spaceSettings)
            physics.lock = lock

            val simpleBodies = PaperSimpleBodies(this, world, physics, this.settings.simpleBodies)
            val terrain = if (settings.terrain.enabled) {
                PaperDynamicTerrain(this, world, physics, settings.terrain)
            } else null
            val entities: PaperEntityStrategy? = null // TODO

            Locked(PaperWorldPhysics(this, world, physics, terrain, entities, simpleBodies), lock)
        }

    fun playerData(player: Player) = players.computeIfAbsent(player) {
        PaperRattlePlayer(this, player)
    }
}

fun World.physicsOrNull() = Rattle.physicsOrNull(this)

fun World.physicsOrCreate() = Rattle.physicsOrCreate(this)

fun World.hasPhysics() = physicsOrNull() != null
