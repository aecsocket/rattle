package io.gitlab.aecsocket.ignacio.paper

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.wrapper.PacketWrapper
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder
import io.gitlab.aecsocket.ignacio.bullet.BulletBackend
import io.gitlab.aecsocket.ignacio.core.*
import io.gitlab.aecsocket.ignacio.core.math.QuatSerializer
import io.gitlab.aecsocket.ignacio.core.math.Vec3
import io.gitlab.aecsocket.ignacio.core.math.Vec3Serializer
import io.gitlab.aecsocket.ignacio.core.util.*
import io.gitlab.aecsocket.ignacio.physx.PhysxBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.serializer.configurate4.ConfigurateComponentSerializer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle.BLOCK_DUST
import org.bukkit.Particle.DustOptions
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

enum class RenderType { POINT, VECTOR, EDGE }

data class WorldSpace(
    val space: IgPhysicsSpace,
    val terrain: SimpleTerrainManager
)

internal data class PlayerData(
    var renderSettings: RenderSettings? = null,
    var statsBar: BossBar? = null
)

internal data class RenderSettings(
    val centerOfMass: Boolean,
    val linearVelocity: Boolean,
    val shape: Boolean
)

internal val cP1 = NamedTextColor.WHITE
internal val cP2 = NamedTextColor.GRAY
internal val cP3 = NamedTextColor.DARK_GRAY
internal val cErr = NamedTextColor.RED

internal operator fun Component.plus(c: Component) = this.append(c)

private inline fun <reified T> TypeSerializerCollection.Builder.registerExact(serializer: TypeSerializer<T>) =
    registerExact(T::class.java, serializer)

private val configOptions = ConfigurationOptions.defaults()
    .serializers {
        it.registerAll(ConfigurateComponentSerializer.configurate().serializers())
        it.registerExact(Vec3Serializer)
        it.registerExact(QuatSerializer)
        it.registerExact(MaterialSerializer)
        it.registerExact(ParticleSerializer)
        it.registerExact(BlockDataSerializer)
        it.registerExact(ColorSerializer)
        it.registerExact(DustOptionsSerializer)
        it.registerExact(ParticleEffectSerializer)
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
        val stepTimeIntervals: List<Double> = listOf(5.0, 15.0, 60.0),
        val stepTimeBarInterval: Double = 1.0,
        val computeTerrain: Boolean = true,
        val geometryRender: GeometryRender = GeometryRender(),
        val renderParticles: Map<RenderType, IgParticleEffect> = mapOf(
            RenderType.POINT to IgParticleEffect(
                BLOCK_DUST,
                data = DustOptions(Color.RED, 0.2f)
            ),
            RenderType.VECTOR to IgParticleEffect(
                BLOCK_DUST,
                data = DustOptions(Color.BLUE, 0.2f)
            ),
            RenderType.EDGE to IgParticleEffect(
                BLOCK_DUST,
                data = DustOptions(Color.YELLOW, 0.2f)
            )
        ),
        val debugMeshSettings: IgMesh.Settings = IgMesh.Settings(),
        val debugMeshItem: IgItemDescriptor = IgItemDescriptor(Material.STONE)
    )

    val physicsThread = IgPhysicsThread(logger)
    val meshes = IgMeshes()
    val lastStepTimes = TimedCache<Long>(0)

    private val mSpaces = HashMap<UUID, WorldSpace>()
    val spaces: Map<UUID, WorldSpace> get() = mSpaces

    lateinit var backend: IgBackend<*> private set
    lateinit var settings: Settings private set

    internal val playerData = HashMap<Player, PlayerData>()
    internal val primitives = IgPrimitives(this)
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
            settings = node.force()
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
                runStep()
            }

            runPlayerData()
        }
        physicsThread.start()
        primitives.enable()
    }

    private fun runStep() {
        executePhysics {
            stepping.set(true)
            lastStepTimes.timeNanos {
                backend.step(mSpaces.map { (_, space) -> space.space })
                mSpaces.forEach world@ { (_, space) ->
                    val (physSpace, terrain) = space
                    val positions = HashSet<TerrainPos>()
                    if (settings.computeTerrain) {
                        physSpace.activeBodies.forEach { body ->
                            if (body !is IgDynamicBody) return@forEach
                            val bounds = body.boundingBox.inflated(Vec3(0.5)) // todo
                            positions.addAll(terrainPosIn(bounds))
                        }
                    }
                    terrain.compute(positions)
                }

            }
            stepping.set(false)
        }
    }

    private fun runPlayerData() {
        val (avg, top5, bottom5) = lastStepTimes.getLastResults((settings.stepTimeBarInterval * 1000).toLong())
        playerData.forEach { (player, data) ->
            val physSpace = physicsSpaceOfOrNull(player.world) ?: return@forEach

            data.statsBar?.let { statsBar ->
                statsBar.name(
                    text(physSpace.countBodies(true), cP1)
                    + text(" awake / ", cP2)
                    + text(physSpace.countBodies(), cP1)
                    + text(" total", cP2)
                    + text(" | ", cP3)
                    + textTiming(avg)
                    + text(" avg / ", cP2)
                    + textTiming(top5)
                    + text(" 5%ile / ", cP2)
                    + textTiming(bottom5)
                    + text(" 95%ile", cP2)
                )
            }

            data.renderSettings?.let { renderSettings ->
                val pointRenderer = pointRendererOf(RenderType.POINT, player)
                val vectorRenderer = pointRendererOf(RenderType.VECTOR, player)
                val edgeRenderer = pointRendererOf(RenderType.EDGE, player)

                executePhysics {
                    val nearby = physSpace.nearbyBodies(player.location.vec(), 16.0)
                    nearby.forEach { body ->
                        val bodyTransform = body.transform
                        val position = bodyTransform.position

                        if (renderSettings.centerOfMass) {
                            pointRenderer.render(position)
                        }

                        if (body is IgDynamicBody && renderSettings.linearVelocity) {
                            val points = settings.geometryRender.line(position, position + body.linearVelocity)
                            points.forEach { vectorRenderer.render(it) }
                        }

                        if (renderSettings.shape) {
                            body.shapes.forEach { shape ->
                                val transform = bodyTransform * shape.transform
                                val points = settings.geometryRender.points(shape.geometry)
                                    .map { transform.apply(it) }
                                points.forEach { edgeRenderer.render(it) }
                            }
                        }
                    }
                }
            }
        }
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

        mSpaces.forEach { (_, space) ->
            space.space.settings = settings.space
        }
        loadInternal()
    }

    internal fun playerData(player: Player) = playerData.computeIfAbsent(player) { PlayerData() }

    fun pointRendererOf(type: RenderType, player: Player): PointRenderer {
        val particle = settings.renderParticles[type]
        return particle?.let {
            PointRenderer { pos ->
                particle.spawn(player, pos)
            }
        } ?: PointRenderer {}
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

    fun physicsSpaceOfOrNull(world: World) = mSpaces[world.uid]?.space

    fun physicsSpaceOf(world: World) = mSpaces.computeIfAbsent(world.uid) {
        val physSpace = backend.createSpace(settings.space)
        WorldSpace(physSpace, SimpleTerrainManager(backend, world, physSpace))
    }.space

    fun removeSpace(world: World) {
        mSpaces.remove(world.uid)?.let { space ->
            backend.destroySpace(space.space)
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
