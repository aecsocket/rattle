package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.Geometry
import io.github.aecsocket.ignacio.core.math.clamp
import io.github.aecsocket.ignacio.core.math.radians
import io.github.aecsocket.ignacio.core.math.sqr
import jolt.*
import jolt.core.*
import jolt.physics.PhysicsSettings
import jolt.physics.PhysicsSystem
import jolt.physics.collision.ObjectLayerPairFilter
import jolt.physics.collision.broadphase.BroadPhaseLayerInterface
import jolt.physics.collision.broadphase.BroadPhaseLayerInterfaceFn
import jolt.physics.collision.broadphase.ObjectVsBroadPhaseLayerFilter
import jolt.physics.collision.shape.*
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

class JoltEngine(var settings: Settings, private val logger: Logger) : IgnacioEngine {
    @ConfigSerializable
    data class Settings(
        val jobs: Jobs = Jobs(),
        val spaces: Spaces = Spaces(),
        val physics: Physics = Physics(),
    ) {
        @ConfigSerializable
        data class Jobs(
            val maxJobs: Int = JobSystem.MAX_PHYSICS_JOBS,
            val maxBarriers: Int = JobSystem.MAX_PHYSICS_BARRIERS,
            val numThreads: Int = 0,
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

    override val build: String

    private val destroyed = DestroyFlag()
    private val arena = MemorySession.openShared()
    private val executorId = AtomicInteger(1)
    val spaces = HashMap<PhysicsSystem, JtPhysicsSpace>()

    val numThreads: Int
    val executor: ExecutorService
    val executorScope: CoroutineScope
    val jobSystem: JobSystem
    val bpLayerInterface: BroadPhaseLayerInterface
    val objBpLayerFilter: ObjectVsBroadPhaseLayerFilter
    val objPairLayerFilter: ObjectLayerPairFilter
    override val layers = object : IgnacioEngine.Layers {
        override val ofObject = object : IgnacioEngine.Layers.OfObject {
            override val static = JtObjectLayer(JObjectLayer(0))
            override val terrain = JtObjectLayer(JObjectLayer(1))
            override val entity = JtObjectLayer(JObjectLayer(2))
            override val moving = JtObjectLayer(JObjectLayer(3))
        }
    }

    init {
        Jolt.load()

        build = "v${Jolt.JOLT_VERSION} ${Jolt.featureSet().joinToString(" ") { it.name }}"

        numThreads =
            if (settings.jobs.numThreads <= 0) clamp(Runtime.getRuntime().availableProcessors() - 2, 1, 16)
            else settings.jobs.numThreads

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
        executor = Executors.newFixedThreadPool(8) { task ->
            Thread(task, "Ignacio-Worker-${executorId.getAndIncrement()}")
        }
        executorScope = CoroutineScope(executor.asCoroutineDispatcher())

        Jolt.registerDefaultAllocator()
        Jolt.createFactory()
        Jolt.registerTypes()
        jobSystem = JobSystem.of(
            settings.jobs.maxJobs,
            settings.jobs.maxBarriers,
            numThreads
        )

        bpLayerInterface = BroadPhaseLayerInterface.of(arena, object : BroadPhaseLayerInterfaceFn {
            override fun getNumBroadPhaseLayers() = 4
            override fun getBroadPhaseLayer(layer: Short) = when (JObjectLayer(layer)) {
                objectLayerStatic -> bpLayerStatic.layer
                objectLayerTerrain -> bpLayerTerrain.layer
                objectLayerEntity -> bpLayerEntity.layer
                objectLayerMoving -> bpLayerMoving.layer
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
        logger.info("Waiting for worker threads")
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS)
        } catch (ex: InterruptedException) {
            logger.warning("Could not wait for worker threads")
        }

        spaces.forEach { (_, space) ->
            space.destroy()
        }

        jobSystem.destroy()
        Jolt.destroyFactory()
        arena.close()
    }

    override fun runTask(block: suspend CoroutineScope.() -> Unit) {
        if (destroyed.marked()) return
        executorScope.launch(block = block)
    }

    override fun createGeometry(settings: GeometrySettings): Geometry {
        val shape: Shape = when (settings) {
            is SphereGeometrySettings -> SphereShape.of(settings.radius)
            is BoxGeometrySettings -> useArena {
                BoxShape.of(settings.halfExtent.toJolt())
            }
            is CapsuleGeometrySettings -> CapsuleShape.of(settings.halfHeight, settings.radius)
            is StaticCompoundGeometrySettings -> useArena {
                StaticCompoundShapeSettings.of().use { compound ->
                    settings.children.forEach { child ->
                        compound.addShape(
                            child.position.toJolt(),
                            child.rotation.toJolt(),
                            (child.geometry as JtGeometry).handle,
                            0
                        )
                    }
                    // TODO use a thread-safe temp allocator
                    compound.create(this).orThrow()
                }
            }
        }
        return JtGeometry(shape)
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
        useArena {
            system.setGravity(settings.gravity.toJolt())
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
