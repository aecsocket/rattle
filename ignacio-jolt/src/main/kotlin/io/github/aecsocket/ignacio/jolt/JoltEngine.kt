package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.alexandria.core.math.Vec3f
import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.Shape
import io.github.aecsocket.alexandria.core.math.clamp
import io.github.aecsocket.alexandria.core.math.radians
import io.github.aecsocket.alexandria.core.math.sqr
import jolt.*
import jolt.core.*
import jolt.geometry.GJKClosestPoint
import jolt.geometry.PointConvexSupport
import jolt.physics.PhysicsSettings
import jolt.physics.PhysicsSystem
import jolt.physics.collision.ObjectLayerFilter
import jolt.physics.collision.ObjectLayerPairFilter
import jolt.physics.collision.broadphase.BroadPhaseLayerFilter
import jolt.physics.collision.broadphase.BroadPhaseLayerInterface
import jolt.physics.collision.broadphase.BroadPhaseLayerInterfaceFn
import jolt.physics.collision.broadphase.ObjectVsBroadPhaseLayerFilter
import jolt.physics.collision.shape.*
import jolt.physics.collision.shape.ConvexShape.Support
import jolt.physics.collision.shape.ConvexShape.SupportBuffer
import kotlinx.coroutines.*
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import java.lang.foreign.MemorySession
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger
import kotlin.math.cos

val objectLayerStatic = JObjectLayer(0)
val objectLayerTerrain = JObjectLayer(1)
val objectLayerEntity = JObjectLayer(2)
val objectLayerMoving = JObjectLayer(3)

val bpLayerStatic = JBroadPhaseLayer(0)
val bpLayerTerrain = JBroadPhaseLayer(0)
val bpLayerEntity = JBroadPhaseLayer(0)
val bpLayerMoving = JBroadPhaseLayer(3)

data class JtBroadPhaseLayer(val layer: JBroadPhaseLayer) : BroadPhaseLayer {
    override fun toString() = "BroadPhaseLayer(${layer.id})"

    override fun equals(other: Any?) = other is JtBroadPhaseLayer && layer == other.layer

    override fun hashCode() = layer.hashCode()
}

data class JtObjectLayer(val layer: JObjectLayer) : ObjectLayer {
    override fun toString() = "ObjectLayer(${layer.id})"

    override fun equals(other: Any?) = other is JtObjectLayer && layer == other.layer

    override fun hashCode() = layer.hashCode()
}

data class JtBroadFilter(
    val broad: BroadPhaseLayerFilter,
    val objects: ObjectLayerFilter,
    val arena: MemorySession,
) : BroadFilter {
    private val destroyed = DestroyFlag()

    fun destroyed() = destroyed.marked()

    override fun destroy() {
        destroyed.mark()
        arena.close()
    }
}

data class JtConvexSupportFunction(
    val handle: Support,
    val arena: MemorySession,
) : SupportFunction {
    private val destroyed = DestroyFlag()

    fun destroyed() = destroyed.marked()

    override fun destroy() {
        destroyed.mark()
        arena.close()
    }
}

data class JtPointSupportFunction(val point: Vec3f) : SupportFunction {
    override fun destroy() {}
}

private const val GJK_TOLERANCE = 1.0e-4f;

class JoltEngine(var settings: Settings, private val logger: Logger) : IgnacioEngine {
    @ConfigSerializable
    data class Settings(
        val threads: Threads = Threads(),
        val jobs: Jobs = Jobs(),
        val spaces: Spaces = Spaces(),
        val physics: Physics = Physics(),
    ) {
        @ConfigSerializable
        data class Threads(
            val physics: Int = 0,
            val workers: Int = 0,
            val terminateTime: Double = 0.0,
        )

        @ConfigSerializable
        data class Jobs(
            val maxJobs: Int = JobSystem.MAX_PHYSICS_JOBS,
            val maxBarriers: Int = JobSystem.MAX_PHYSICS_BARRIERS,
            // must be at least `maxContactConstraints` * 864
            // mConstraints = (ContactConstraint *)inContext->mTempAllocator->Allocate(mMaxConstraints * sizeof(ContactConstraint));
            val tempAllocatorSize: Int = 20 * 1024 * 1024,
        )

        @ConfigSerializable
        data class Spaces(
            val maxBodies: Int = 65536,
            val maxBodyPairs: Int = 65536,
            val maxContactConstraints: Int = 16384,
            val numBodyMutexes: Int = 0,
            val collisionSteps: Int = 1,
            val integrationSubSteps: Int = 1,
        )

        @ConfigSerializable
        data class Physics(
            val maxInFlightBodyPairs: Int = 16384,
            val stepListenersBatchSize: Int = 8,
            val stepListenerBatchesPerJob: Int = 1,
            val baumgarte: Float = 0.2f,
            val speculativeContactDistance: Float = 0.02f,
            val penetrationSlop: Float = 0.02f,
            val linearCastThreshold: Float = 0.75f,
            val linearCastMaxPenetration: Float = 0.25f,
            val manifoldToleranceSq: Float = 1.0e-6f,
            val maxPenetrationDistance: Float = 0.2f,
            val bodyPairCacheMaxDeltaPositionSq: Float = sqr(0.001f),
            val bodyPairCacheCosMaxDeltaRotationDiv2: Float = cos(radians(2.0f) / 2),
            val contactNormalCosMaxDeltaRotation: Float = cos(radians(5.0f)),
            val contactPointPreserveLambdaMaxDistSq: Float = sqr(0.01f),
            val numVelocitySteps: Int = 10,
            val numPositionSteps: Int = 2,
            val minVelocityForRestitution: Float = 1.0f,
            val timeBeforeSleep: Float = 0.5f,
            val pointVelocitySleepThreshold: Float = 0.03f,
            val constraintWarmStart: Boolean = true,
            val useBodyPairContactCache: Boolean = true,
            val useManifoldReduction: Boolean = true,
            val allowSleeping: Boolean = true,
            val checkActiveEdges: Boolean = true,
        )
    }

    private val destroyed = DestroyFlag()
    override val build: String

    private val arena = MemorySession.openShared()
    private val executorId = AtomicInteger(1)
    private val executor: ExecutorService
    private val executorScope: CoroutineScope

    val spaces = HashMap<PhysicsSystem, JtPhysicsSpace>()
    val jobSystem: JobSystem
    val bpLayerInterface: BroadPhaseLayerInterface
    val objBpLayerFilter: ObjectVsBroadPhaseLayerFilter
    val objPairLayerFilter: ObjectLayerPairFilter

    override val layers = object : IgnacioEngine.Layers {
        override val ofBroadPhase = object : IgnacioEngine.Layers.OfBroadPhase {
            override val static = JtBroadPhaseLayer(bpLayerStatic)
            override val terrain = JtBroadPhaseLayer(bpLayerTerrain)
            override val entity = JtBroadPhaseLayer(bpLayerEntity)
            override val moving = JtBroadPhaseLayer(bpLayerMoving)
        }

        override val ofObject = object : IgnacioEngine.Layers.OfObject {
            override val static = JtObjectLayer(objectLayerStatic)
            override val terrain = JtObjectLayer(objectLayerTerrain)
            override val entity = JtObjectLayer(objectLayerEntity)
            override val moving = JtObjectLayer(objectLayerMoving)
        }
    }

    override val filters = object : IgnacioEngine.Filters {
        override fun createBroad(
            broad: BroadFilterTest,
            objects: ObjectFilterTest,
        ): BroadFilter {
            val arena = MemorySession.openShared()
            return JtBroadFilter(
                BroadPhaseLayerFilter.of(arena) { layer ->
                    broad.test(JtBroadPhaseLayer(JBroadPhaseLayer(layer)))
                },
                ObjectLayerFilter.of(arena) { layer ->
                    objects.test(JtObjectLayer(JObjectLayer(layer)))
                },
                arena
            )
        }
    }

    override val gjk = object : IgnacioEngine.GJK {
        /*
                fun supportOf(shape: Shape): SupportFunction?

        fun supportOf(point: Vec3f): SupportFunction

        fun collides(supportA: SupportFunction, supportB: SupportFunction): Boolean

        data class ClosestPoints(
            val a: Vec3f,
            val b: Vec3f,
            val distanceSq: Float,
        )

        fun closestPoints(supportA: SupportFunction, supportB: SupportFunction): ClosestPoints
         */

        override fun supportOf(shape: Shape): SupportFunction? {
            shape as JtShape
            return shape.convexHandle?.let { handle ->
                MemorySession.openShared().run {
                    val buffer = SupportBuffer.of(this)
                    val support = handle.getSupportFunction(ConvexShape.SupportMode.EXCLUDE_CONVEX_RADIUS, buffer, Vec3f.One.toJolt())
                    support?.let { JtConvexSupportFunction(it, this) }
                }
            }
        }

        override fun supportOf(point: Vec3f): SupportFunction {
            return JtPointSupportFunction(point)
        }

        override fun collides(supportA: SupportFunction, supportB: SupportFunction): Boolean {
            supportA as? JtConvexSupportFunction
                ?: throw IllegalArgumentException("supportA must be a convex support function")
            return useMemory {
                val gjk = GJKClosestPoint.of(this)
                val result = when (supportB) {
                    is JtConvexSupportFunction -> gjk.intersects(
                        supportA.handle,
                        supportB.handle,
                        GJK_TOLERANCE,
                        Vec3f.Zero.toJolt()
                    )
                    is JtPointSupportFunction -> gjk.intersects(
                        supportA.handle,
                        PointConvexSupport.of(this, supportB.point.toJolt()),
                        GJK_TOLERANCE,
                        supportB.point.toJolt(),
                    )
                    else -> throw IllegalArgumentException("supportB must be a convex or point support function")
                }
                result
            }
        }

        override fun closestPoints(
            supportA: SupportFunction,
            supportB: SupportFunction
        ): IgnacioEngine.GJK.ClosestPoints {
            supportA as? JtConvexSupportFunction
                ?: throw IllegalArgumentException("supportA must be a convex support function")
            return useMemory {
                val gjk = GJKClosestPoint.of(this)
                val outA = FVec3()
                val outB = FVec3()
                val distanceSq = when (supportB) {
                    is JtConvexSupportFunction -> gjk.getClosestPoints(
                        supportA.handle,
                        supportB.handle,
                        GJK_TOLERANCE,
                        Float.MAX_VALUE,
                        Vec3f.X.toJolt(),
                        outA,
                        outB,
                    )
                    is JtPointSupportFunction -> gjk.getClosestPoints(
                        supportA.handle,
                        PointConvexSupport.of(this, supportB.point.toJolt()),
                        GJK_TOLERANCE,
                        Float.MAX_VALUE,
                        supportB.point.toJolt(),
                        outA,
                        outB,
                    )
                    else -> throw IllegalArgumentException("supportB must be a convex or point support function")
                }
                IgnacioEngine.GJK.ClosestPoints(
                    outA.toIgnacio(),
                    outB.toIgnacio(),
                    distanceSq,
                )
            }
        }
    }

    private fun sanitizeNumThreads(num: Int) =
        if (num > 0) num
        else clamp(Runtime.getRuntime().availableProcessors() - 2, 1, 16)

    init {
        Jolt.load()

        build = "v${Jolt.JOLT_VERSION} ${Jolt.featureSet().joinToString(" ") { it.name }}"

        // TODO have one thread pool rather than Jolt and Java pools
        // TODO allow multithreading with barriers?
        /*
                           [ ChunkGenerate (1, 1) ]
        ChunkLoad -> ----- [ ChunkGenerate (1, 2) ]  -> DONE
                           [ ChunkGenerate (1, 3) ]
                           [ ...                  ]

                                                    [ Raycast (Player1) ]
                Raycast -> ------------------------ [ Raycast (Player2) ] -> DONE
                                                    [ ...               ]
         */
        executor = Executors.newFixedThreadPool(sanitizeNumThreads(settings.threads.workers)) { task ->
            Thread(task, "Ignacio-Worker-${executorId.getAndIncrement()}")
        }
        executorScope = CoroutineScope(executor.asCoroutineDispatcher())

        Jolt.registerDefaultAllocator()
        Jolt.createFactory()
        Jolt.registerTypes()
        jobSystem = JobSystem.of(
            settings.jobs.maxJobs,
            settings.jobs.maxBarriers,
            sanitizeNumThreads(settings.threads.physics),
        )

        bpLayerInterface = BroadPhaseLayerInterface.of(arena, object : BroadPhaseLayerInterfaceFn {
            override fun getNumBroadPhaseLayers() = 4
            override fun getBroadPhaseLayer(layer: Short) = when (JObjectLayer(layer)) {
                objectLayerStatic -> bpLayerStatic.id
                objectLayerTerrain -> bpLayerTerrain.id
                objectLayerEntity -> bpLayerEntity.id
                objectLayerMoving -> bpLayerMoving.id
                else -> throw IllegalArgumentException("Invalid layer $layer")
            }
        })

        objBpLayerFilter = ObjectVsBroadPhaseLayerFilter.of(arena) { layer1, layer2 ->
            when (JObjectLayer(layer1)) {
                objectLayerStatic, objectLayerTerrain, objectLayerEntity -> JBroadPhaseLayer(layer2) == bpLayerMoving
                objectLayerMoving -> true
                else -> false
            }
        }

        objPairLayerFilter = ObjectLayerPairFilter.of(arena) { layer1, layer2 ->
            when (JObjectLayer(layer1)) {
                objectLayerStatic, objectLayerTerrain, objectLayerEntity -> JObjectLayer(layer2) == objectLayerMoving
                objectLayerMoving -> true
                else -> false
            }
        }
    }

    override fun destroy() {
        destroyed.mark()
        executor.shutdown()
        logger.info("Waiting ${settings.threads.terminateTime}s for worker threads")
        try {
            if (!executor.awaitTermination((settings.threads.terminateTime * 1000).toLong(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow()
            }
        } catch (ex: InterruptedException) {
            executor.shutdownNow()
            logger.warning("Could not wait for worker threads")
        }

        spaces.toMap().forEach { (_, space) ->
            space.destroy()
        }
        spaces.clear()

        jobSystem.destroy()
        Jolt.destroyFactory()
        arena.close()
    }

    override fun runTask(block: Runnable) {
        executor.execute(block)
    }

    override fun launchTask(block: suspend CoroutineScope.() -> Unit) {
        if (destroyed.marked()) return
        executorScope.launch(block = block)
    }

    override fun createShape(geometry: Geometry): Shape {
        val shape: JShape = useMemory {
            val result: ShapeResult = when (geometry) {
                is SphereGeometry -> SphereShapeSettings.of(geometry.radius).use { settings ->
                    settings.density = geometry.density
                    settings.create(this)
                }
                is BoxGeometry -> BoxShapeSettings.of(geometry.halfExtent.toJolt()).use { settings ->
                    settings.density = geometry.density
                    settings.create(this)
                }
                is CapsuleGeometry -> CapsuleShapeSettings.of(geometry.halfHeight, geometry.radius).use { settings ->
                    settings.density = geometry.density
                    settings.create(this)
                }
                is StaticCompoundGeometry -> StaticCompoundShapeSettings.of().use { settings ->
                    geometry.children.forEach { child ->
                        settings.addShape(
                            child.position.toJolt(),
                            child.rotation.toJolt(),
                            (child.shape as JtShape).handle,
                            0
                        )
                    }
                    settings.create(this)
                }
            }
            result.orThrow()
        }
        return JtShape(shape)
    }

    override fun createSpace(settings: PhysicsSpace.Settings): PhysicsSpace {
        val tempAllocator = TempAllocator.of(this.settings.jobs.tempAllocatorSize)
        val system = PhysicsSystem.of(
            this.settings.spaces.maxBodies,
            this.settings.spaces.numBodyMutexes,
            this.settings.spaces.maxBodyPairs,
            this.settings.spaces.maxContactConstraints,
            bpLayerInterface,
            objBpLayerFilter,
            objPairLayerFilter
        )

        val physics = this.settings.physics
        useMemory {
            val physicsSettings = PhysicsSettings.of(this)
            system.getPhysicsSettings(physicsSettings)
            physicsSettings.apply {
                maxInFlightBodyPairs = physics.maxInFlightBodyPairs
                stepListenersBatchSize = physics.stepListenersBatchSize
                stepListenerBatchesPerJob = physics.stepListenerBatchesPerJob
                baumgarte = physics.baumgarte
                speculativeContactDistance = physics.speculativeContactDistance
                penetrationSlop = physics.penetrationSlop
                linearCastThreshold = physics.linearCastThreshold
                linearCastMaxPenetration = physics.linearCastMaxPenetration
                manifoldToleranceSq = physics.manifoldToleranceSq
                maxPenetrationDistance = physics.maxPenetrationDistance
                bodyPairCacheMaxDeltaPositionSq = physics.bodyPairCacheMaxDeltaPositionSq
                bodyPairCacheCosMaxDeltaRotationDiv2 = physics.bodyPairCacheCosMaxDeltaRotationDiv2
                contactNormalCosMaxDeltaRotation = physics.contactNormalCosMaxDeltaRotation
                contactPointPreserveLambdaMaxDistSq = physics.contactPointPreserveLambdaMaxDistSq
                numVelocitySteps = physics.numVelocitySteps
                numPositionSteps = physics.numPositionSteps
                minVelocityForRestitution = physics.minVelocityForRestitution
                timeBeforeSleep = physics.timeBeforeSleep
                pointVelocitySleepThreshold = physics.pointVelocitySleepThreshold
                constraintWarmStart = physics.constraintWarmStart
                useBodyPairContactCache = physics.useBodyPairContactCache
                useManifoldReduction = physics.useManifoldReduction
                allowSleeping = physics.allowSleeping
                checkActiveEdges = physics.checkActiveEdges
            }
            system.setPhysicsSettings(physicsSettings)
        }

        return JtPhysicsSpace(this, system, tempAllocator, settings).also {
            spaces[system] = it
        }
    }
}
