package io.github.aecsocket.ignacio.paper

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.wrapper.PacketWrapper
import io.github.aecsocket.alexandria.core.Logging
import io.github.aecsocket.alexandria.core.LoggingList
import io.github.aecsocket.alexandria.core.serializer.alexandriaCoreSerializers
import io.github.aecsocket.alexandria.paper.AlexandriaApiPlugin
import io.github.aecsocket.alexandria.paper.ItemDescriptor
import io.github.aecsocket.alexandria.paper.extension.runRepeating
import io.github.aecsocket.alexandria.paper.fallbackLocale
import io.github.aecsocket.alexandria.paper.seralizer.alexandriaPaperSerializers
import io.github.aecsocket.glossa.core.MessageProxy
import io.github.aecsocket.glossa.core.messageProxy
import io.github.aecsocket.ignacio.core.PhysicsSpace
import io.github.aecsocket.ignacio.core.TimestampedList
import io.github.aecsocket.ignacio.core.math.Ray
import io.github.aecsocket.ignacio.core.math.sp
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
        val physicsSpaces: PhysicsSpace.Settings = PhysicsSpace.Settings(),
        val engineTimings: EngineTimings = EngineTimings(),
        val primitiveModels: PrimitiveModels = PrimitiveModels(),
    ) : AlexandriaApiPlugin.Settings {
        @ConfigSerializable
        data class EngineTimings(
            val buffer: Double = 60.0,
            val buffersToDisplay: List<Double> = listOf(5.0, 15.0, 60.0),
        )

        @ConfigSerializable
        data class PrimitiveModels(
            val box: ItemDescriptor = ItemDescriptor(Material.STONE),
            val sphere: ItemDescriptor = ItemDescriptor(Material.STONE),
        )
    }

    val renders = StandRenders()
    val primitiveBodies = PrimitiveBodies(this)
    private val mEngineTimings = timestampedList<Long>(0)
    val engineTimings: TimestampedList<Long> get() = mEngineTimings
    val worldPhysics = HashMap<World, PhysicsSpace>()
    val updatingPhysics = AtomicBoolean(false)

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

        runRepeating {
            primitiveBodies.update()

            engine.runTask {
                if (updatingPhysics.getAndSet(true)) return@runTask

                val start = System.nanoTime()
                worldPhysics.forEach { (_, physics) ->
                    physics.update(deltaTime)
                }
                val end = System.nanoTime()

                mEngineTimings.add(end - start)
                updatingPhysics.set(false)
            }

            // TODO
            engine.runTask {
                Bukkit.getOnlinePlayers().forEach { player ->
                    val physics = physicsInOr(player.world) ?: return@forEach
                    val nearby = physics.bodiesNear(player.location.position(), 16f)
                    val casts = physics.rayCastBodies(
                        ray = Ray(player.eyeLocation.position(), player.location.direction.vec3d().sp()),
                        distance = 16f,
                    )
                    val cast = physics.rayCastBody(
                        Ray(player.eyeLocation.position(), player.location.direction.vec3d().sp()),
                        16f
                    )
                    player.sendActionBar(Component.text("cast = $cast | casts = ${casts.size} | nearby = ${nearby.size}"))
                }
            }
        }
    }

    override fun onDisable() {
        worldPhysics.forEach { (_, space) ->
            engine.destroySpace(space)
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

    fun physicsInOr(world: World) = worldPhysics[world]

    fun physicsIn(world: World) = worldPhysics.computeIfAbsent(world) {
        engine.createSpace(settings.physicsSpaces)
    }
}

internal fun Player.sendPacket(packet: PacketWrapper<*>) =
    PacketEvents.getAPI().playerManager.sendPacket(this, packet)
