package io.github.aecsocket.ignacio.paper

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.wrapper.PacketWrapper
import io.github.aecsocket.alexandria.BossBarDescriptor
import io.github.aecsocket.alexandria.Logging
import io.github.aecsocket.alexandria.LoggingList
import io.github.aecsocket.alexandria.Synchronized
import io.github.aecsocket.alexandria.paper.AlexandriaPlugin
import io.github.aecsocket.alexandria.paper.ItemDescriptor
import io.github.aecsocket.alexandria.paper.extension.registerEvents
import io.github.aecsocket.alexandria.paper.fallbackLocale
import io.github.aecsocket.alexandria.paper.seralizer.alexandriaPaperSerializers
import io.github.aecsocket.glossa.MessageProxy
import io.github.aecsocket.glossa.messageProxy
import io.github.aecsocket.ignacio.IgnacioEngine
import io.github.aecsocket.ignacio.PhysicsSpace
import io.github.aecsocket.ignacio.TimestampedList
import io.github.aecsocket.ignacio.jolt.JoltEngine
import io.github.aecsocket.ignacio.paper.render.DisplayRenders
import io.github.aecsocket.ignacio.paper.render.Renders
import io.github.aecsocket.ignacio.paper.world.*
import io.github.aecsocket.ignacio.timestampedList
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder
import io.papermc.paper.util.Tick
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.ObjectMapper
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.HashMap

private lateinit var instance: Ignacio
val IgnacioAPI get() = instance

private val configOptions: ConfigurationOptions = ConfigurationOptions.defaults()
    .serializers { it
        .registerAll(alexandriaPaperSerializers)
        .registerAnnotatedObjects(ObjectMapper.factoryBuilder()
            .addDiscoverer(dataClassFieldDiscoverer())
            .build()
        )
    }

enum class TerrainStrategies(
    private val factory: TerrainStrategyFactory,
) {
    NONE            ({ _, _, _ -> NoOpTerrainStrategy }),
    MOVING_SLICE    ({ ignacio, world, physics -> MovingSliceTerrainStrategy(ignacio, world, physics, MovingSliceTerrainStrategy.Settings()) });

    fun create(ignacio: Ignacio, world: World, physics: PhysicsSpace) = factory.create(ignacio, world, physics)
}

enum class EntityStrategies(
    private val factory: EntityStrategyFactory,
) {
    NONE    ({ _, _, _ -> NoOpEntityStrategy });

    fun create(ignacio: Ignacio, world: World, physics: PhysicsSpace) = factory.create(ignacio, world, physics)
}

class Ignacio : AlexandriaPlugin(Manifest("ignacio",
    accentColor = TextColor.color(0xdeab14),
    languageResources = listOf(
        "lang/en-US.yml",
    ),
    savedResources = listOf(
        "settings.yml",
    ),
)) {
    companion object {
        @JvmStatic
        fun api() = IgnacioAPI
    }

    @ConfigSerializable
    data class Settings(
        override val defaultLocale: Locale = fallbackLocale,
        val worlds: Worlds = Worlds(),
        val primitiveBodies: PrimitiveBodies = PrimitiveBodies(),
        val engineTimings: EngineTimings = EngineTimings(),
        val jolt: JoltEngine.Settings = JoltEngine.Settings(),
    ) : AlexandriaPlugin.Settings {
        @ConfigSerializable
        data class Worlds(
            val space: PhysicsSpace.Settings = PhysicsSpace.Settings(),
            val deltaTimeMultiplier: Float = 1.0f,
            val terrainStrategy: TerrainStrategies = TerrainStrategies.MOVING_SLICE,
            val entityStrategy: EntityStrategies = EntityStrategies.NONE,
        )

        @ConfigSerializable
        data class PrimitiveBodies(
            val models: Models = Models(),
            val teleportThreshold: Double = 16.0,
        ) {
            @ConfigSerializable
            data class Models(
                val box: ItemDescriptor = ItemDescriptor(Material.STONE),
                val sphere: ItemDescriptor = ItemDescriptor(Material.STONE),
            )
        }

        @ConfigSerializable
        data class EngineTimings(
            val buffer: Double = 60.0,
            val buffersToDisplay: List<Double> = listOf(5.0, 15.0, 60.0),
            val barBuffer: Double = 5.0,
            val bar: BossBarDescriptor = BossBarDescriptor(),
        )
    }

    override lateinit var settings: Settings private set
    lateinit var engine: IgnacioEngine private set
    lateinit var messages: MessageProxy<IgnacioMessages> private set
    private var worldDeltaTime = 0f
    private val players = ConcurrentHashMap<Player, IgnacioPlayer>()
    private val worldMap = Synchronized(HashMap<World, PhysicsWorld>())
    val renders: Renders = DisplayRenders()
    val primitiveBodies = PrimitiveBodies(this)
    val primitiveRenders = PrimitiveRenders(this)
    private val updatingPhysics = AtomicBoolean(false)
    private val mEngineTimings = timestampedList<Long>(0)
    val engineTimings: TimestampedList<Long> get() = mEngineTimings

    init {
        instance = this
    }

    override fun onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this))
        PacketEvents.getAPI().settings
            .checkForUpdates(false)
            .bStats(true)
        PacketEvents.getAPI().load()

        super.onLoad()

        // TODO other plugins should be able to use this builder
        engine = JoltEngine.Builder(settings.jolt, logger).build()
        logger.info("${ProcessHandle.current().pid()}: Loaded ${engine::class.simpleName} ${engine.build}")
    }

    override fun onEnable() {
        PacketEvents.getAPI().init()
        IgnacioCommand(this)
        registerEvents(IgnacioListener(this))
        scheduling.onServer().runRepeating { onServerUpdate() }
    }

    override fun onDisable() {
        PacketEvents.getAPI().terminate()
        // shutdown -> remove -> destroy, since we need to make sure no worker/phys threads are working while we destroy
        engine.shutdown()
        worldMap.synchronized { worldMap ->
            worldMap.forEach { (_, world) ->
                world.physics.destroy()
            }
            worldMap.clear()
        }
        engine.destroy()
    }

    override fun configOptions() = configOptions

    override fun loadSettings(node: ConfigurationNode?) {
        settings = node?.get() ?: Settings()
    }

    override fun load(log: LoggingList) {
        messages = glossa.messageProxy()
        worldDeltaTime = (Tick.of(1).toMillis() / 1000.0f) * settings.worlds.deltaTimeMultiplier
        mEngineTimings.buffer = (settings.engineTimings.buffer * 1000).toLong()
        primitiveBodies.load()
    }

    override fun reload(log: Logging) {
        when (val engine = engine) {
            is JoltEngine -> engine.settings = settings.jolt
        }
    }

    private fun onServerUpdate() {
        engine.launchTask {
            if (updatingPhysics.getAndSet(true)) return@launchTask

            val start = System.nanoTime()
            onPhysicsUpdate()
            val end = System.nanoTime()

            mEngineTimings.add(end - start)
            updatingPhysics.set(false)
        }
    }

    private fun onPhysicsUpdate() {
        primitiveBodies.onPhysicsUpdate()
        val worldMap = worldMap.synchronized { it.toMap() }
        worldMap.forEach { (_, world) ->
            world.startPhysicsUpdate(worldDeltaTime)
        }
        worldMap.forEach { (_, world) ->
            world.joinPhysicsUpdate()
        }
    }

    fun playerData(player: Player) = players.computeIfAbsent(player) {
        IgnacioPlayer(this, player).also { wrapper ->
            scheduling.onEntity(player).runRepeating {
                wrapper.onEntityUpdate()
            }
        }
    }

    internal fun removePlayer(player: Player) {
        players.remove(player)
    }

    val worlds = Worlds()
    inner class Worlds {
        // safety: it's just a field read
        val count get() = worldMap.leak().size

        operator fun contains(world: World) = worldMap.synchronized { it.containsKey(world) }

        operator fun get(world: World) = worldMap.synchronized { it[world] }

        fun getOrCreate(world: World) = worldMap.synchronized { worldMap ->
            worldMap.computeIfAbsent(world) {
                val physics = engine.space(settings.worlds.space)
                PhysicsWorld(
                    world,
                    physics,
                    settings.worlds.terrainStrategy.create(this@Ignacio, world, physics),
                    settings.worlds.entityStrategy.create(this@Ignacio, world, physics),
                )
            }
        }

        fun destroy(world: World) = worldMap.synchronized { worldMap ->
            worldMap.remove(world)?.destroy()
        }

        fun all() = worldMap.synchronized { it.toMap() }
    }
}

fun Player.sendPacket(packet: PacketWrapper<*>) =
    PacketEvents.getAPI().playerManager.sendPacket(this, packet)
