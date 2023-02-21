package io.github.aecsocket.ignacio.paper

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.wrapper.PacketWrapper
import io.github.aecsocket.alexandria.paper.AlexandriaApiPlugin
import io.github.aecsocket.alexandria.paper.extension.runRepeating
import io.github.aecsocket.alexandria.core.Logging
import io.github.aecsocket.alexandria.core.LoggingList
import io.github.aecsocket.alexandria.core.extension.registerExact
import io.github.aecsocket.alexandria.core.serializers.alexandriaCoreSerializers
import io.github.aecsocket.alexandria.paper.ItemDescriptor
import io.github.aecsocket.alexandria.paper.fallbackLocale
import io.github.aecsocket.alexandria.paper.seralizer.alexandriaPaperSerializers
import io.github.aecsocket.glossa.core.*
import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.math.Vec3dSerializer
import io.github.aecsocket.ignacio.core.math.Vec3fSerializer
import io.github.aecsocket.ignacio.jolt.JoltEngine
import io.github.aecsocket.ignacio.paper.display.StandRenders
import io.github.aecsocket.ignacio.physx.PhysxEngine
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder
import io.gitlab.aecsocket.ignacio.bullet.BulletEngine
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
import kotlin.random.Random

private lateinit var instance: Ignacio
val IgnacioAPI get() = instance

enum class IgnacioEngines {
    NONE,
    JOLT,
    PHYSX,
    BULLET,
}

private val configOptions: ConfigurationOptions = ConfigurationOptions.defaults()
    .serializers { it
        .registerAll(alexandriaCoreSerializers)
        .registerAll(alexandriaPaperSerializers)
        .registerExact(Vec3fSerializer)
        .registerExact(Vec3dSerializer)
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
        val engine: IgnacioEngines = IgnacioEngines.NONE,
        val jolt: JoltEngine.Settings = JoltEngine.Settings(),
        val physx: PhysxEngine.Settings = PhysxEngine.Settings(),
        val bullet: BulletEngine.Settings = BulletEngine.Settings(),
        val deltaTimeMultiplier: Float = 1f,
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
    val primitiveBodies = PrimitiveBodies()
    private val mEngineTimings = timestampedList<Long>(0)
    val engineTimings: TimestampedList<Long> get() = mEngineTimings
    val worldPhysics = HashMap<World, PhysicsSpace>()

    override lateinit var settings: Settings private set
    lateinit var messages: MessageProxy<IgnacioMessages> private set
    lateinit var engine: IgnacioEngine private set

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

        engine = when (settings.engine) {
            IgnacioEngines.JOLT -> JoltEngine(settings.jolt)
            IgnacioEngines.PHYSX -> PhysxEngine(settings.physx, logger)
            IgnacioEngines.BULLET -> BulletEngine(settings.bullet)
            IgnacioEngines.NONE -> throw RuntimeException("Physics engine is not set")
        }

        logger.info("Loaded ${engine::class.simpleName} ${engine.build}")
    }

    override fun onEnable() {
        IgnacioCommand(this)

        runRepeating {
            primitiveBodies.update()
            val start = System.nanoTime()
            worldPhysics.forEach { (_, physics) ->
                physics.update(0.05f * settings.deltaTimeMultiplier)
            }
            val end = System.nanoTime()
            mEngineTimings.add(end - start)
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
        mEngineTimings.buffer = (settings.engineTimings.buffer * 1000).toLong()
    }

    override fun reload(log: Logging) {
        when (val engine = engine) {
            is JoltEngine -> engine.settings = settings.jolt
            is PhysxEngine -> engine.settings = settings.physx
            is BulletEngine -> engine.settings = settings.bullet
        }
    }

    fun physicsIn(world: World) = worldPhysics.computeIfAbsent(world) {
        engine.createSpace(PhysicsSpace.Settings())
    }
}

internal fun Player.sendPacket(packet: PacketWrapper<*>) =
    PacketEvents.getAPI().playerManager.sendPacket(this, packet)
