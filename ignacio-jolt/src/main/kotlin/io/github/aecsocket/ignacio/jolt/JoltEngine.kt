package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.Geometry
import io.github.aecsocket.ignacio.core.math.clamp
import io.github.aecsocket.ignacio.core.math.radians
import io.github.aecsocket.ignacio.core.math.sqr
import jolt.*
import jolt.core.*
import jolt.kotlin.*
import jolt.physics.PhysicsSettings
import jolt.physics.PhysicsSystem
import jolt.physics.collision.ObjectLayerPairFilter
import jolt.physics.collision.broadphase.BroadPhaseLayerInterface
import jolt.physics.collision.broadphase.ObjectVsBroadPhaseLayerFilter
import jolt.physics.collision.shape.*
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import java.lang.foreign.MemorySession
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger
import kotlin.math.cos

val objectLayerNonMoving = ObjectLayer(0)
val objectLayerMoving = ObjectLayer(1)

val bpLayerNonMoving = BroadPhaseLayer(0)
val bpLayerMoving = BroadPhaseLayer(1)

class JoltEngine(var settings: Settings, logger: Logger) : IgnacioEngine {
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

    private val destroy = DestroyFlag()
    private val arena = MemorySession.openShared()
    private val executorId = AtomicInteger(1)
    val spaces = HashMap<PhysicsSystem, JtPhysicsSpace>()

    val numThreads: Int
    val executor: ExecutorService
    val jobSystem: JobSystem
    val bpLayerInterface: BroadPhaseLayerInterface
    val objBpLayerFilter: ObjectVsBroadPhaseLayerFilter
    val objPairLayerFilter: ObjectLayerPairFilter

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
        executor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "Ignacio-Worker-${executorId.getAndIncrement()}")
        }

        Jolt.registerDefaultAllocator()
        Jolt.createFactory()
        Jolt.registerTypes()
        jobSystem = JobSystem.of(
            settings.jobs.maxJobs,
            settings.jobs.maxBarriers,
            numThreads
        )

        bpLayerInterface = BroadPhaseLayerInterface(arena,
            getNumBroadPhaseLayers = { 2 },
            getBroadPhaseLayer = { layer -> when (layer) {
                objectLayerNonMoving -> bpLayerNonMoving
                objectLayerMoving -> bpLayerMoving
                else -> throw IllegalArgumentException("Invalid layer $layer")
            } }
        )

        objBpLayerFilter = ObjectVsBroadPhaseLayerFilter(arena,
            shouldCollide = { layer1, layer2 -> when (layer1) {
                objectLayerNonMoving -> layer2 == bpLayerMoving
                objectLayerMoving -> true
                else -> false
            } }
        )

        objPairLayerFilter = ObjectLayerPairFilter(arena,
            shouldCollide = { layer1, layer2 -> when (layer1) {
                objectLayerNonMoving -> layer2 == objectLayerNonMoving
                objectLayerMoving -> true
                else -> false
            } }
        )
    }

    override fun destroy() {
        destroy.mark()
        executor.shutdown()

        spaces.forEach { (_, space) ->
            space.destroy()
        }

        jobSystem.destroy()
        Jolt.destroyFactory()
        arena.close()
    }

    override fun runTask(task: Runnable) {
        executor.execute(task)
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
