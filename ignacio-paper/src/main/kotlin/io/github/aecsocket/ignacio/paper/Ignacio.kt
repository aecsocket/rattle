package io.github.aecsocket.ignacio.paper

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.wrapper.PacketWrapper
import io.github.aecsocket.alexandria.core.BossBarSettings
import io.github.aecsocket.alexandria.core.Logging
import io.github.aecsocket.alexandria.core.LoggingList
import io.github.aecsocket.alexandria.core.serializer.alexandriaCoreSerializers
import io.github.aecsocket.alexandria.paper.AlexandriaApiPlugin
import io.github.aecsocket.alexandria.paper.ItemDescriptor
import io.github.aecsocket.alexandria.paper.extension.registerEvents
import io.github.aecsocket.alexandria.paper.fallbackLocale
import io.github.aecsocket.alexandria.paper.seralizer.alexandriaPaperSerializers
import io.github.aecsocket.glossa.core.MessageProxy
import io.github.aecsocket.glossa.core.messageProxy
import io.github.aecsocket.ignacio.core.IgnacioEngine
import io.github.aecsocket.ignacio.core.PhysicsSpace
import io.github.aecsocket.ignacio.core.TimestampedList
import io.github.aecsocket.ignacio.core.timestampedList
import io.github.aecsocket.ignacio.jolt.JoltEngine
import io.github.aecsocket.ignacio.paper.display.StandRenders
import io.github.aecsocket.ignacio.paper.world.*
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

private lateinit var instance: Ignacio
val IgnacioAPI get() = instance

private val configOptions: ConfigurationOptions = ConfigurationOptions.defaults()
    .serializers { it
        .registerAll(alexandriaCoreSerializers)
        .registerAll(alexandriaPaperSerializers)
        .registerAnnotatedObjects(ObjectMapper.factoryBuilder()
            .addDiscoverer(dataClassFieldDiscoverer())
            .build()
        )
    }

enum class TerrainStrategies(
    private val factory: TerrainStrategyFactory
) {
    NONE        ({ _, _, _ -> NoOpTerrainStrategy() }),
    SLICE       ({ engine, world, physics -> SliceTerrainStrategy(engine, world, physics) });

    fun create(engine: IgnacioEngine, world: World, physics: PhysicsSpace) = factory.create(engine, world, physics)
}

enum class EntityStrategies(
    private val factory: EntityStrategyFactory
) {
    NONE    ({ _, _, _ -> NoOpEntityStrategy() }),
    DEFAULT ({ engine, world, physics -> DefaultEntityStrategy(engine, physics) });

    fun create(engine: IgnacioEngine, world: World, physics: PhysicsSpace) = factory.create(engine, world, physics)
}

enum class PlayerStrategies(
    private val factory: PlayerStrategyFactory
) {
    NONE    ({ _, _, _ -> NoOpPlayerStrategy() }),
    DEFAULT ({ engine, world, physics -> DefaultPlayerStrategy(engine, world, physics) });

    fun create(engine: IgnacioEngine, world: World, physics: PhysicsSpace) = factory.create(engine, world, physics)
}

class Ignacio : AlexandriaApiPlugin(Manifest("ignacio",
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
        val deltaTimeMultiplier: Float = 1f,
        val jolt: JoltEngine.Settings = JoltEngine.Settings(),
        val worlds: Worlds = Worlds(),
        val primitiveModels: PrimitiveModels = PrimitiveModels(),
        val engineTimings: EngineTimings = EngineTimings(),
        val barDisplay: BossBarSettings = BossBarSettings(),
    ) : AlexandriaApiPlugin.Settings {
        @ConfigSerializable
        data class Worlds(
            val terrainStrategy: TerrainStrategies = TerrainStrategies.SLICE,
            val entityStrategy: EntityStrategies = EntityStrategies.DEFAULT,
            val playerStrategy: PlayerStrategies = PlayerStrategies.DEFAULT,
            val spaceSettings: PhysicsSpace.Settings = PhysicsSpace.Settings(),
        )

        @ConfigSerializable
        data class PrimitiveModels(
            val box: ItemDescriptor = ItemDescriptor(Material.STONE),
            val sphere: ItemDescriptor = ItemDescriptor(Material.STONE),
        )

        @ConfigSerializable
        data class EngineTimings(
            val buffer: Double = 60.0,
            val buffersToDisplay: List<Double> = listOf(5.0, 15.0, 60.0),
            val barBuffer: Double = 5.0,
        )
    }

    val renders = StandRenders()
    val primitiveBodies = PrimitiveBodies(this)
    private val mEngineTimings = timestampedList<Long>(0)
    val engineTimings: TimestampedList<Long> get() = mEngineTimings
    val updatingPhysics = AtomicBoolean(false)
    private val players = ConcurrentHashMap<Player, IgnacioPlayer>()

    // TODO the concurrency model here needs to be improved
    // who should access this on what thread?
    private val worldPhysics = ConcurrentHashMap<World, PhysicsWorld>()

    interface Worlds {
        operator fun get(world: World): PhysicsWorld?

        operator fun contains(world: World): Boolean

        fun getOrCreate(world: World): PhysicsWorld

        fun destroy(world: World)

        fun all(): Map<World, PhysicsWorld>
    }
    val worlds = object : Worlds {
        override fun get(world: World) = worldPhysics[world]

        override fun contains(world: World) = worldPhysics.contains(world)

        override fun getOrCreate(world: World) = worldPhysics.computeIfAbsent(world) {
            val physics = engine.createSpace(settings.worlds.spaceSettings)
            PhysicsWorld(
                world = world,
                physics = physics,
                terrain = settings.worlds.terrainStrategy.create(engine, world, physics),
                entities = settings.worlds.entityStrategy.create(engine, world, physics),
                players = settings.worlds.playerStrategy.create(engine, world, physics),
            ).also {
                it.terrain.onChunksLoad(world.loadedChunks.asList())
            }
        }

        override fun destroy(world: World) {
            val physics = worldPhysics[world] ?: return
            worldPhysics.remove(world)
            engine.launchTask {
                physics.destroy()
            }
        }

        override fun all() = worldPhysics
    }

    override lateinit var settings: Settings private set
    lateinit var engine: IgnacioEngine private set
    lateinit var messages: MessageProxy<IgnacioMessages> private set
    private var deltaTime = 0f

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

        engine = JoltEngine(settings.jolt, logger)
        logger.info("${ProcessHandle.current().pid()}: Loaded ${engine::class.simpleName} (${engine.build})")
    }

    override fun onEnable() {
        IgnacioCommand(this)
        registerEvents(IgnacioListener(this))
    }

    override fun onDisable() {
        engine.destroy()
    }

    override fun configOptions() = configOptions

    override fun loadSettings(node: ConfigurationNode?) {
        settings = node?.get() ?: Settings()
    }

    override fun load(log: LoggingList) {
        messages = glossa.messageProxy()
        deltaTime = (Tick.of(1).toMillis() / 1000f) * settings.deltaTimeMultiplier
        mEngineTimings.buffer = (settings.engineTimings.buffer * 1000).toLong()
    }

    override fun reload(log: Logging) {
        when (val engine = engine) {
            is JoltEngine -> engine.settings = settings.jolt
        }
    }

    fun playerData(player: Player) = players.computeIfAbsent(player) {
        IgnacioPlayer(this, player)
    }

    internal fun removePlayerData(player: Player) {
        players.remove(player)
    }

    internal fun update() {
        primitiveBodies.tickUpdate()
        players.forEach { (_, player) ->
            player.update()
        }
        worldPhysics.forEach { (_, world) ->
            world.tickUpdate()
        }

        engine.launchTask {
            if (updatingPhysics.getAndSet(true)) return@launchTask

            val start = System.nanoTime()
            primitiveBodies.physicsUpdate()
            worldPhysics.forEach { (_, world) ->
                world.physicsUpdate(deltaTime)
            }
            val end = System.nanoTime()

            mEngineTimings.add(end - start)
            updatingPhysics.set(false)
        }
    }
}

internal fun Player.sendPacket(packet: PacketWrapper<*>) =
    PacketEvents.getAPI().playerManager.sendPacket(this, packet)

fun ignacioBodyName(value: String) = "Ignacio-$value"
