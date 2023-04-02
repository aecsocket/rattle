package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.*
import io.github.aecsocket.klam.clamp
import io.github.aecsocket.klam.radians
import jolt.Deletable
import jolt.Jolt
import jolt.core.JobSystem
import jolt.core.TempAllocator
import jolt.physics.PhysicsSettings
import jolt.physics.PhysicsSystem
import jolt.physics.collision.ObjectLayerFilter
import jolt.physics.collision.ObjectLayerPairFilter
import jolt.physics.collision.broadphase.BroadPhaseLayerFilter
import jolt.physics.collision.broadphase.BroadPhaseLayerInterface
import jolt.physics.collision.broadphase.BroadPhaseLayerInterfaceFn
import jolt.physics.collision.broadphase.ObjectVsBroadPhaseLayerFilter
import jolt.physics.collision.shape.BoxShape
import jolt.physics.collision.shape.CapsuleShape
import jolt.physics.collision.shape.CompoundShapeSettings
import jolt.physics.collision.shape.CylinderShape
import jolt.physics.collision.shape.MutableCompoundShapeSettings
import jolt.physics.collision.shape.Shape
import jolt.physics.collision.shape.SphereShape
import jolt.physics.collision.shape.StaticCompoundShapeSettings
import jolt.physics.collision.shape.TaperedCapsuleShapeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import java.lang.foreign.MemorySession
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.cos

private const val BP_LAYER_STATIC: Byte = 0
private const val BP_LAYER_MOVING: Byte = 1
private const val BP_LAYER_TERRAIN: Byte = 2

private const val OBJ_LAYER_STATIC: Short = 0
private const val OBJ_LAYER_MOVING: Short = 1
private const val OBJ_LAYER_TERRAIN: Short = 2
private const val OBJ_LAYER_ENTITY: Short = 3

private const val BODY_LAYER_MAX_VALUE = Short.MAX_VALUE
private const val BODY_LAYER_NUM_BITS = 16

private fun numThreads(num: Int) =
    if (num > 0) num
    else clamp(Runtime.getRuntime().availableProcessors() - 1, 1, 16)

fun <T : Deletable, R> T.use(block: (T) -> R): R {
    val result = block(this)
    delete()
    return result
}

data class JtBodyLayer(val id: Short) : BodyLayer

data class JtBodyFlag(val id: Short) : BodyFlag

data class JtBodyContactFilter(val id: Short) : BodyContactFilter

data class JtLayerFilter(
    val broad: BroadPhaseLayerFilter,
    val objects: ObjectLayerFilter,
    val arena: MemorySession,
) : LayerFilter {
    override fun destroy() {
        arena.close()
    }
}

data class JtBodyFilter(
    val body: jolt.physics.collision.BodyFilter,
    val arena: MemorySession,
) : BodyFilter {
    override fun destroy() {
        arena.close()
    }
}

data class JtShapeFilter(
    val shape: jolt.physics.collision.ShapeFilter,
    val arena: MemorySession,
) : ShapeFilter {
    override fun destroy() {
        arena.close()
    }
}

internal data class ObjectLayerData(
    val bpLayer: Byte,
    val bpLayerMask: Byte, // byte = 8 bits; 1 bit per broad-phase layer (bitfield)
)

class JoltEngine internal constructor(
    var settings: Settings,
    private val objectLayers: Array<ObjectLayerData>,
) : IgnacioEngine {
    @ConfigSerializable
    data class Settings(
        val jobs: Jobs = Jobs(),
        val space: Space = Space(),
        val physics: Physics = Physics(),
    ) {
        @ConfigSerializable
        data class Jobs(
            val physicsThreads: Int = 0,
            val workerThreads: Int = 0,
            val threadTerminateTime: Double = 0.0,
            val maxJobs: Int = JobSystem.MAX_PHYSICS_JOBS,
            val maxBarriers: Int = JobSystem.MAX_PHYSICS_BARRIERS,
        )

        @ConfigSerializable
        data class Space(
            val maxBodies: Int = 65536,
            val numBodyMutexes: Int = 0,
            val maxBodyPairs: Int = 65536,
            val maxContactConstraints: Int = 16384,
            val tempAllocatorSize: Int = 32 * 1024 * 1024,
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
            val bodyPairCacheMaxDeltaPositionSq: Float = 0.001f * 0.001f,
            val bodyPairCacheCosMaxDeltaRotationDiv2: Float = cos(radians(2.0f) / 2.0f),
            val contactNormalCosMaxDeltaRotation: Float = cos(radians(5.0f)),
            val contactPointPreserveLambdaMaxDistSq: Float = 0.01f * 0.01f,
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
    private val arena: MemorySession
    private val executorId = AtomicInteger(1)
    private val executor: ExecutorService
    private val executorScope: CoroutineScope

    val jobSystem: JobSystem
    val bpLayerInterface: BroadPhaseLayerInterface
    val objBpLayerFilter: ObjectVsBroadPhaseLayerFilter
    val objLayerPairFilter: ObjectLayerPairFilter

    private val spaces = HashMap<PhysicsSystem, JtPhysicsSpace>()

    init {
        Jolt.load()
        build = "v${Jolt.JOLT_VERSION} (${Jolt.featureSet().joinToString(" ")})"

        Jolt.registerDefaultAllocator()
        Jolt.createFactory()
        Jolt.registerTypes()
        arena = MemorySession.openShared()
        executor = Executors.newFixedThreadPool(numThreads(settings.jobs.workerThreads)) { task ->
            Thread(task, "Ignacio-Worker-${executorId.getAndIncrement()}")
        }
        executorScope = CoroutineScope(executor.asCoroutineDispatcher())

        jobSystem = JobSystem.of(settings.jobs.maxJobs, settings.jobs.maxBarriers, numThreads(settings.jobs.physicsThreads))

        bpLayerInterface = BroadPhaseLayerInterface.of(arena, object : BroadPhaseLayerInterfaceFn {
            override fun getNumBroadPhaseLayers() = objectLayers.size
            override fun getBroadPhaseLayer(layer: Short) = objectLayers[layer.toInt()].bpLayer
        })

        objBpLayerFilter = ObjectVsBroadPhaseLayerFilter.of(arena) { layer1, layer2 ->
            // check if the `layer2`th bit is set in `layer1`'s broad-phase layer mask
            val bpMask = 1 shl layer2.toInt()
            objectLayers[layer1.toInt()].bpLayerMask.toInt() and bpMask != 0
        }

        objLayerPairFilter = ObjectLayerPairFilter.of(arena) { layer1, layer2 ->
            // TODO

            when (layer1) {
                OBJ_LAYER_STATIC -> layer2 == OBJ_LAYER_MOVING
                OBJ_LAYER_MOVING -> true
                OBJ_LAYER_TERRAIN -> layer2 == OBJ_LAYER_MOVING
                OBJ_LAYER_ENTITY -> layer2 == OBJ_LAYER_MOVING
                else -> throw IllegalArgumentException("Invalid object layer $layer1")
            }
        }
    }

    override fun destroy() {
        destroyed.mark()

        spaces.toMap().forEach { (_, space) ->
            space.destroy()
        }
        spaces.clear()

        arena.close()
    }

    override val layers = object : IgnacioEngine.Layers {
        override val static = JtBodyLayer(OBJ_LAYER_STATIC)
        override val moving = JtBodyLayer(OBJ_LAYER_MOVING)
        override val terrain = JtBodyLayer(OBJ_LAYER_TERRAIN)
        override val entity = JtBodyLayer(OBJ_LAYER_ENTITY)
    }

    override val filters = object : IgnacioEngine.Filters {
        override val anyLayer = JtLayerFilter(BroadPhaseLayerFilter.passthrough(), ObjectLayerFilter.passthrough(), openArena())

        override val anyBody = JtBodyFilter(jolt.physics.collision.BodyFilter.passthrough(), openArena())

        override val anyShape = JtShapeFilter(jolt.physics.collision.ShapeFilter.passthrough(), openArena())
    }

    override fun runTask(block: Runnable) {
        executor.execute(block)
    }

    override fun launchTask(block: suspend CoroutineScope.() -> Unit) {
        if (destroyed.marked()) return
        executorScope.launch(block = block)
    }

    override fun contactFilter(layer: BodyLayer, flags: Set<BodyFlag>): BodyContactFilter {
        layer as JtBodyLayer
        @Suppress("UNCHECKED_CAST")
        flags as Set<JtBodyFlag>
        // TODO add flags
        return JtBodyContactFilter(layer.id)
    }

    override fun shape(geom: Geometry) = JtShape(pushArena { arena ->
        fun CompoundShapeSettings.addChild(child: CompoundChild) {
            addShape(
                arena.asJolt(child.position),
                arena.asJolt(child.rotation),
                (child.shape as JtShape).handle,
                0,
            )
        }

        val handle: Shape = when (geom) {
            is SphereGeometry -> SphereShape.of(geom.radius)
            is BoxGeometry -> BoxShape.of(arena.asJolt(geom.halfExtent), geom.convexRadius)
            is CapsuleGeometry -> CapsuleShape.of(geom.halfHeight, geom.radius)
            is TaperedCapsuleGeometry -> TaperedCapsuleShapeSettings.of(geom.halfHeight, geom.topRadius, geom.bottomRadius).use { settings ->
                settings.create(arena).orThrow()
            }
            is CylinderGeometry -> CylinderShape.of(geom.halfHeight, geom.radius, geom.convexRadius)
            is StaticCompoundGeometry -> StaticCompoundShapeSettings.of().use { settings ->
                geom.children.forEach { child ->
                    settings.addChild(child)
                }
                settings.create(arena).orThrow()
            }
            is MutableCompoundGeometry -> MutableCompoundShapeSettings.of().use { settings ->
                geom.children.forEach { child ->
                    settings.addChild(child)
                }
                settings.create(arena).orThrow()
            }
        }
        handle
    })

    override fun space(settings: PhysicsSpace.Settings): PhysicsSpace {
        val engineSettings = this.settings
        val physics = PhysicsSystem.of(
            engineSettings.space.maxBodies,
            engineSettings.space.numBodyMutexes,
            engineSettings.space.maxBodyPairs,
            engineSettings.space.maxContactConstraints,
            bpLayerInterface,
            objBpLayerFilter,
            objLayerPairFilter,
        )
        val tempAllocator = TempAllocator.of(engineSettings.space.tempAllocatorSize)
        pushArena { arena ->
            val physicsSettings = PhysicsSettings.of(arena)
            physics.getPhysicsSettings(physicsSettings)
            physicsSettings.maxInFlightBodyPairs = engineSettings.physics.maxInFlightBodyPairs
            physicsSettings.stepListenersBatchSize = engineSettings.physics.stepListenersBatchSize
            physicsSettings.stepListenerBatchesPerJob = engineSettings.physics.stepListenerBatchesPerJob
            physicsSettings.baumgarte = engineSettings.physics.baumgarte
            physicsSettings.speculativeContactDistance = engineSettings.physics.speculativeContactDistance
            physicsSettings.penetrationSlop = engineSettings.physics.penetrationSlop
            physicsSettings.linearCastThreshold = engineSettings.physics.linearCastThreshold
            physicsSettings.linearCastMaxPenetration = engineSettings.physics.linearCastMaxPenetration
            physicsSettings.manifoldToleranceSq = engineSettings.physics.manifoldToleranceSq
            physicsSettings.maxPenetrationDistance = engineSettings.physics.maxPenetrationDistance
            physicsSettings.bodyPairCacheMaxDeltaPositionSq = engineSettings.physics.bodyPairCacheMaxDeltaPositionSq
            physicsSettings.bodyPairCacheCosMaxDeltaRotationDiv2 = engineSettings.physics.bodyPairCacheCosMaxDeltaRotationDiv2
            physicsSettings.contactNormalCosMaxDeltaRotation = engineSettings.physics.contactNormalCosMaxDeltaRotation
            physicsSettings.contactPointPreserveLambdaMaxDistSq = engineSettings.physics.contactPointPreserveLambdaMaxDistSq
            physicsSettings.numVelocitySteps = engineSettings.physics.numVelocitySteps
            physicsSettings.numPositionSteps = engineSettings.physics.numPositionSteps
            physicsSettings.minVelocityForRestitution = engineSettings.physics.minVelocityForRestitution
            physicsSettings.timeBeforeSleep = engineSettings.physics.timeBeforeSleep
            physicsSettings.pointVelocitySleepThreshold = engineSettings.physics.pointVelocitySleepThreshold
            physicsSettings.constraintWarmStart = engineSettings.physics.constraintWarmStart
            physicsSettings.useBodyPairContactCache = engineSettings.physics.useBodyPairContactCache
            physicsSettings.useManifoldReduction = engineSettings.physics.useManifoldReduction
            physicsSettings.allowSleeping = engineSettings.physics.allowSleeping
            physicsSettings.checkActiveEdges = engineSettings.physics.checkActiveEdges
            physics.setPhysicsSettings(physicsSettings)
        }
        return JtPhysicsSpace(this, physics, tempAllocator, settings)
    }

    class Builder(
        private val settings: Settings,
    ) : IgnacioEngine.Builder {
        private val layers = mutableListOf(
            ObjectLayerData(BP_LAYER_STATIC,  0b010), // static
            ObjectLayerData(BP_LAYER_MOVING,  0b111), // moving
            ObjectLayerData(BP_LAYER_TERRAIN, 0b100), // terrain
            ObjectLayerData(BP_LAYER_MOVING,  0b111), // entity
        )
        private var flags = 0

//        override fun defineBodyLayer(type: BodyLayerType, collidesWith: Set<BodyLayerType>): BodyLayer {
//            if (layers.size >= BODY_LAYER_MAX_VALUE)
//                throw IllegalStateException("More than $BODY_LAYER_MAX_VALUE body layers defined")
//            val result = JtBodyLayer(layers.size.toShort())
//            var bpLayerMask = 0
//            collidesWith.forEach { collideWith ->
//                bpLayerMask = bpLayerMask or (1 shl collideWith.ordinal)
//            }
//            layers += ObjectLayerData(type.ordinal.toByte(), bpLayerMask.toByte())
//            return result
//        }

//        override fun defineBodyFlag(): BodyFlag {
//            if (flags >= BODY_LAYER_MAX_VALUE)
//                throw IllegalStateException("More than $BODY_LAYER_MAX_VALUE body flags defined")
//            val result = JtBodyFlag(flags.toShort())
//            flags += 1
//            return result
//        }

        override fun build(): JoltEngine {
            val layerBits = 32 - layers.size.countLeadingZeroBits()
            val flagBits = flags
            if (layerBits + flagBits > BODY_LAYER_NUM_BITS)
                throw IllegalStateException("${layers.size} body layers defined ($layerBits bits), $flags body flags defined ($flagBits bits); must use maximum of $BODY_LAYER_NUM_BITS bits")
            return JoltEngine(settings, layers.toTypedArray())
        }
    }
}
