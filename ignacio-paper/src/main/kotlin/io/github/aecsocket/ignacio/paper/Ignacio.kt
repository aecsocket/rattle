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
import io.github.aecsocket.alexandria.paper.extension.runRepeating
import io.github.aecsocket.alexandria.paper.fallbackLocale
import io.github.aecsocket.alexandria.paper.seralizer.alexandriaPaperSerializers
import io.github.aecsocket.glossa.core.MessageProxy
import io.github.aecsocket.glossa.core.messageProxy
import io.github.aecsocket.ignacio.core.PhysicsSpace
import io.github.aecsocket.ignacio.core.TimestampedList
import io.github.aecsocket.ignacio.core.math.Ray
import io.github.aecsocket.ignacio.core.math.f
import io.github.aecsocket.ignacio.core.serializer.ignacioCoreSerializers
import io.github.aecsocket.ignacio.core.timestampedList
import io.github.aecsocket.ignacio.jolt.JoltEngine
import io.github.aecsocket.ignacio.paper.display.StandRenders
import io.github.aecsocket.ignacio.paper.util.position
import io.github.aecsocket.ignacio.paper.util.vec3d
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder
import io.papermc.paper.util.Tick
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Bukkit
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
        .registerAll(ignacioCoreSerializers)
        .registerAnnotatedObjects(ObjectMapper.factoryBuilder()
            .addDiscoverer(dataClassFieldDiscoverer())
            .build()
        )
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
        val terrain: Terrain = Terrain(),
        val physicsSpaces: PhysicsSpace.Settings = PhysicsSpace.Settings(),
        val primitiveModels: PrimitiveModels = PrimitiveModels(),
        val engineTimings: EngineTimings = EngineTimings(),
        val barDisplay: BossBarSettings = BossBarSettings(),
    ) : AlexandriaApiPlugin.Settings {
        @ConfigSerializable
        data class Terrain(
            val autogenerate: Boolean = true,
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

    interface Worlds {
        operator fun get(world: World): WorldPhysics?

        fun getOrCreate(world: World): WorldPhysics

        fun destroy(world: World)

        fun all(): Map<World, WorldPhysics>
    }

    val renders = StandRenders()
    val primitiveBodies = PrimitiveBodies(this)
    private val mEngineTimings = timestampedList<Long>(0)
    val engineTimings: TimestampedList<Long> get() = mEngineTimings
    val updatingPhysics = AtomicBoolean(false)
    internal val players = ConcurrentHashMap<Player, IgnacioPlayer>()

    private val worldPhysics = ConcurrentHashMap<World, WorldPhysics>()
    val worlds = object : Worlds {
        override fun get(world: World) = worldPhysics[world]

        override fun getOrCreate(world: World) = worldPhysics.computeIfAbsent(world) {
            WorldPhysics(this@Ignacio, world, engine.createSpace(settings.physicsSpaces)).also {
                if (settings.terrain.autogenerate) {
                    world.loadedChunks.forEach { chunk ->
                        it.load(chunk)
                    }
                }
            }
        }

        override fun destroy(world: World) {
            val physics = worldPhysics[world] ?: return
            physics.destroy()
            worldPhysics.remove(world)
        }

        override fun all() = worldPhysics
    }

    override lateinit var settings: Settings private set
    lateinit var engine: JoltEngine private set
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

        runRepeating {
            primitiveBodies.update()
            players.forEach { (_, player) ->
                player.update()
            }

            engine.runTask {
                if (updatingPhysics.getAndSet(true)) return@runTask

                val start = System.nanoTime()
                worldPhysics.forEach { (_, world) ->
                    world.physics.update(deltaTime)
                }
                val end = System.nanoTime()

                mEngineTimings.add(end - start)
                updatingPhysics.set(false)
            }

            // TODO
            engine.runTask {
                Bukkit.getOnlinePlayers().forEach { player ->
                    val (physics) = worlds[player.world] ?: return@forEach
                    val nearby = physics.broadQuery.overlapSphere(player.location.position(), 16f)
                    val casts = physics.narrowQuery.rayCastBodies(
                        ray = Ray(player.eyeLocation.position(), player.location.direction.vec3d().f()),
                        distance = 16f,
                    )
                    val cast = physics.narrowQuery.rayCastBody(
                        Ray(player.eyeLocation.position(), player.location.direction.vec3d().f()),
                        16f
                    )
                    player.sendActionBar(Component.text("cast = $cast | casts = ${casts.size} | nearby = ${nearby.size}"))
                }
            }
        }
    }

    override fun onDisable() {
        worldPhysics.forEach { (_, world) ->
            world.physics.destroy()
        }
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
        engine.settings = settings.jolt
    }

    fun playerData(player: Player) = players.computeIfAbsent(player) {
        IgnacioPlayer(this, player)
    }

    internal fun removePlayerData(player: Player) {
        players.remove(player)
    }
}

internal fun Player.sendPacket(packet: PacketWrapper<*>) =
    PacketEvents.getAPI().playerManager.sendPacket(this, packet)
