package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.math.clamp
import io.github.aecsocket.ignacio.core.math.radians
import io.github.aecsocket.ignacio.core.math.sqr
import jolt.*
import jolt.core.*
import jolt.kotlin.BroadPhaseLayer
import jolt.kotlin.ObjectLayer
import jolt.physics.PhysicsSettings
import jolt.physics.PhysicsSystem
import jolt.physics.collision.ObjectLayerPairFilter
import jolt.physics.collision.broadphase.BroadPhaseLayerInterface
import jolt.physics.collision.broadphase.ObjectVsBroadPhaseLayerFilter
import jolt.physics.collision.shape.BoxShape
import jolt.physics.collision.shape.CapsuleShape
import jolt.physics.collision.shape.Shape
import jolt.physics.collision.shape.SphereShape
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import kotlin.math.cos

val OBJECT_LAYER_NON_MOVING = ObjectLayer(0)
val OBJECT_LAYER_MOVING = ObjectLayer(1)

val BP_LAYER_NON_MOVING = BroadPhaseLayer(0)
val BP_LAYER_MOVING = BroadPhaseLayer(1)

class JoltEngine(var settings: Settings) : IgnacioEngine {
    @ConfigSerializable
    data class Settings(
        val jobs: Jobs = Jobs(),
        val spaces: Spaces = Spaces(),
        val physics: Physics = Physics(),
    ) {
        @ConfigSerializable
        data class Jobs(
            val maxJobs: Int = PhysicsSettings.MAX_PHYSICS_JOBS,
            val maxBarriers: Int = PhysicsSettings.MAX_PHYSICS_BARRIERS,
            val numThreads: Int = 0,
            // must be at least `maxContactConstraints` * 864
            // mConstraints = (ContactConstraint *)inContext->mTempAllocator->Allocate(mMaxConstraints * sizeof(ContactConstraint));
            val tempAllocatorSize: Long = 20 * 1024 * 1024,
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

    val spaces = HashMap<PhysicsSystem, JtPhysicsSpace>()

    val numThreads: Int
    val jobSystem: JobSystem
    val bpLayerInterface: BroadPhaseLayerInterface
    val objBpLayerFilter: ObjectVsBroadPhaseLayerFilter
    val objPairLayerFilter: ObjectLayerPairFilter

    init {
        JoltEnvironment.load()

        build = "v??? (${JoltEnvironment.featureList().joinToString(" ") { it.name }})"

        numThreads =
            if (settings.jobs.numThreads < 0) clamp(Runtime.getRuntime().availableProcessors() - 2, 1, 16)
            else settings.jobs.numThreads

        JoltEnvironment.registerDefaultAllocator()
        RTTIFactory.setInstance(RTTIFactory())
        JoltEnvironment.registerTypes()
        jobSystem = JobSystemThreadPool(
            settings.jobs.maxJobs,
            settings.jobs.maxBarriers,
            numThreads
        )

        bpLayerInterface = object : BroadPhaseLayerInterface() {
            override fun getNumBroadPhaseLayers() = 2
            override fun getBroadPhaseLayer(layer: Int) = when (ObjectLayer(layer)) {
                OBJECT_LAYER_NON_MOVING -> BP_LAYER_NON_MOVING
                OBJECT_LAYER_MOVING -> BP_LAYER_MOVING
                else -> throw IllegalArgumentException("Invalid layer $layer")
            }.id
            override fun getBroadPhaseLayerName(layer: Byte) = when (BroadPhaseLayer(layer)) {
                BP_LAYER_NON_MOVING -> "NON_MOVING"
                BP_LAYER_MOVING -> "MOVING"
                else -> throw RuntimeException()
            }
        }

        objBpLayerFilter = object : ObjectVsBroadPhaseLayerFilter() {
            override fun shouldCollide(layer1: Int, layer2: Byte) = when (ObjectLayer(layer1)) {
                OBJECT_LAYER_NON_MOVING -> BroadPhaseLayer(layer2) == BP_LAYER_MOVING
                OBJECT_LAYER_MOVING -> true
                else -> false
            }
        }

        objPairLayerFilter = object : ObjectLayerPairFilter() {
            override fun shouldCollide(layer1: Int, layer2: Int) = when (ObjectLayer(layer1)) {
                OBJECT_LAYER_NON_MOVING -> ObjectLayer(layer2) == OBJECT_LAYER_NON_MOVING
                OBJECT_LAYER_MOVING -> true
                else -> false
            }
        }
    }

    override fun destroy() {
        spaces.forEach { (_, space) ->
            space.destroy()
        }

        objPairLayerFilter.delete()
        objBpLayerFilter.delete()
        bpLayerInterface.delete()
        jobSystem.delete()
        RTTIFactory.getInstance()?.delete()
        RTTIFactory.setInstance(null)
    }

    fun shapeOf(geometry: Geometry): Shape {
        return when (geometry) {
            is SphereGeometry -> SphereShape(geometry.radius)
            is BoxGeometry -> BoxShape(geometry.halfExtent.jolt())
            is CapsuleGeometry -> CapsuleShape(geometry.halfHeight, geometry.radius)
            else -> throw IllegalArgumentException("Unsupported geometry type ${geometry::class.simpleName}")
        }
    }

    override fun createSpace(settings: PhysicsSpace.Settings): PhysicsSpace {
        val system = PhysicsSystem()
        val tempAllocator = TempAllocatorImpl.ofBytes(this.settings.jobs.tempAllocatorSize)
        system.init(
            this.settings.spaces.maxBodies,
            this.settings.spaces.numBodyMutexes,
            this.settings.spaces.maxBodyPairs,
            this.settings.spaces.maxContactConstraints,
            bpLayerInterface,
            objBpLayerFilter,
            objPairLayerFilter
        )

        system.gravity = settings.gravity.jolt()

        val physics = this.settings.physics
        system.physicsSettings.apply {
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
        return JtPhysicsSpace(this, system, tempAllocator).also {
            spaces[system] = it
        }
    }

    override fun destroySpace(space: PhysicsSpace) {
        space as JtPhysicsSpace
        spaces.remove(space.handle)
        space.destroy()
    }
}
