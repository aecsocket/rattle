package io.gitlab.aecsocket.ignacio.paper

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.wrapper.PacketWrapper
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder
import io.gitlab.aecsocket.ignacio.bullet.BulletBackend
import io.gitlab.aecsocket.ignacio.core.*
import io.gitlab.aecsocket.ignacio.core.math.QuatSerializer
import io.gitlab.aecsocket.ignacio.core.math.Vec3Serializer
import io.gitlab.aecsocket.ignacio.core.util.TimedCache
import io.gitlab.aecsocket.ignacio.core.util.timeNanos
import io.gitlab.aecsocket.ignacio.physx.PhysxBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.ObjectMapper
import org.spongepowered.configurate.serialize.TypeSerializer
import org.spongepowered.configurate.serialize.TypeSerializerCollection
import org.spongepowered.configurate.util.NamingSchemes
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

private const val PATH_SETTINGS = "settings.conf"

private lateinit var instance: Ignacio
val IgnacioAPI get() = instance

enum class IgBackends { BULLET, PHYSX, NONE }

private inline fun <reified T> TypeSerializerCollection.Builder.registerExact(serializer: TypeSerializer<T>) =
    registerExact(T::class.java, serializer)

private val configOptions = ConfigurationOptions.defaults()
    .serializers {
        it.registerExact(Vec3Serializer)
        it.registerExact(QuatSerializer)
        it.registerAnnotatedObjects(ObjectMapper.factoryBuilder()
            .addDiscoverer(dataClassFieldDiscoverer())
            .defaultNamingScheme(NamingSchemes.SNAKE_CASE)
            .build()
        )
    }

class Ignacio : JavaPlugin() {
    companion object {
        @JvmStatic
        fun instance() = IgnacioAPI
    }

    @ConfigSerializable
    data class Settings(
        val backend: IgBackends = IgBackends.NONE,
        val bullet: BulletBackend.Settings = BulletBackend.Settings(),
        val physx: PhysxBackend.Settings = PhysxBackend.Settings(),
        val space: IgPhysicsSpace.Settings = IgPhysicsSpace.Settings(),
        val stepTimeIntervals: List<Double> = listOf(5.0, 15.0, 60.0)
    )

    val physicsThread = IgPhysicsThread(logger)
    val meshes = IgMeshes()
    val lastStepTimes = TimedCache<Long>(0)

    private val _spaces = HashMap<UUID, IgPhysicsSpace>()
    val spaces: Map<UUID, IgPhysicsSpace> get() = _spaces

    lateinit var backend: IgBackend<*> private set
    lateinit var settings: Settings private set

    private val stepping = AtomicBoolean(false)

    init {
        instance = this
    }

    private fun loadSettingsNode() = HoconConfigurationLoader.builder()
        .defaultOptions(configOptions)
        .file(dataFolder.resolve(PATH_SETTINGS))
        .build().load()

    override fun onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this))
        PacketEvents.getAPI().settings
            .checkForUpdates(false)
            .bStats(true)
        PacketEvents.getAPI().load()

        if (!dataFolder.exists()) {
            saveResource(PATH_SETTINGS, false)
        }

        try {
            val node = loadSettingsNode()
            settings = node.igForce()
        } catch (ex: Exception) {
            throw RuntimeException("Could not load settings", ex)
        }

        backend = when (settings.backend) {
            IgBackends.BULLET -> BulletBackend(settings.bullet, physicsThread, dataFolder, logger)
            IgBackends.PHYSX -> PhysxBackend(settings.physx, physicsThread, logger)
            else -> throw RuntimeException("Ignacio has not been set up with a backend - specify `backend` in $PATH_SETTINGS")
        }
        loadInternal()
    }

    override fun onEnable() {
        PacketEvents.getAPI().init()
        IgnacioCommand(this)

        registerEvents(IgnacioEventListener(this))

        runRepeating {
            meshes.update()
            if (!stepping.get()) {
                executePhysics {
                    stepping.set(true)
                    lastStepTimes.timeNanos {
                        backend.step(_spaces.values)
                    }
                    stepping.set(false)
                }
            }
        }
        physicsThread.start()
    }

    override fun onDisable() {
        PacketEvents.getAPI().terminate()
        physicsThread.execute {
            backend.destroy()
        }
        physicsThread.destroy()
    }

    private fun loadInternal() {
        lastStepTimes.buffer = (settings.stepTimeIntervals.max() * 1000).toLong()
    }

    fun reload() {
        settings = try {
            loadSettingsNode().get { Settings() }
        } catch (ex: Exception) {
            logger.severe("Could not load settings")
            ex.printStackTrace()
            Settings()
        }

        when (val backend = backend) {
            is BulletBackend -> backend.reload(settings.bullet)
            is PhysxBackend -> backend.reload(settings.physx)
        }
        loadInternal()
    }

    fun executePhysics(task: Runnable) {
        physicsThread.execute(task)
    }

    fun <T> executePhysics(task: Supplier<T>): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        executePhysics {
            val result = task.get()
            future.complete(result)
        }
        return future
    }

    fun spaceOfOrNull(world: World) = _spaces[world.uid]

    fun spaceOf(world: World) = _spaces.computeIfAbsent(world.uid) {
        backend.createSpace(settings.space)
    }

    fun removeSpace(world: World) {
        _spaces.remove(world.uid)?.let { space ->
            backend.destroySpace(space)
        }
    }
}

suspend fun <T> Ignacio.runPhysics(block: suspend CoroutineScope.() -> T): T {
    return executePhysics(Supplier {
        runBlocking { block() }
    }).await()
}

internal fun Player.sendPacket(packet: PacketWrapper<*>) =
    PacketEvents.getAPI().playerManager.sendPacket(this, packet)
