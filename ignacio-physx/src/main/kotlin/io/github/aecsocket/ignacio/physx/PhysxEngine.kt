package io.github.aecsocket.ignacio.physx

import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.math.Transform
import io.github.aecsocket.ignacio.core.math.clamp
import org.lwjgl.system.MemoryStack
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import physx.PxTopLevelFunctions
import physx.common.*
import physx.geometry.PxGeometry
import physx.geometry.PxPlaneGeometry
import physx.physics.*
import java.util.logging.Level
import java.util.logging.Logger

class PsErrorCallback(private val logger: Logger) : PxErrorCallbackImpl() {
    override fun reportError(code: PxErrorCodeEnum, message: String, file: String, line: Int) {
        val level = when (code) {
            PxErrorCodeEnum.eMASK_ALL,
            PxErrorCodeEnum.eNO_ERROR,
            PxErrorCodeEnum.eDEBUG_INFO -> Level.INFO

            PxErrorCodeEnum.eDEBUG_WARNING,
            PxErrorCodeEnum.eINVALID_PARAMETER,
            PxErrorCodeEnum.eINVALID_OPERATION,
            PxErrorCodeEnum.ePERF_WARNING -> Level.WARNING

            PxErrorCodeEnum.eOUT_OF_MEMORY,
            PxErrorCodeEnum.eINTERNAL_ERROR,
            PxErrorCodeEnum.eABORT -> Level.SEVERE
        }
        logger.log(level, "PhysX: $message")
    }
}

class PhysxEngine(var settings: Settings, logger: Logger) : IgnacioEngine {
    @ConfigSerializable
    data class Settings(
        val numThreads: Int = 0,
    )

    override val build: String

    val physicsThread = PhysicsThread("PhysX", logger)
    val spaces = HashMap<PxScene, PsPhysicsSpace>()

    val numThreads: Int

    val allocator: PxDefaultAllocator
    val errorCb: PxErrorCallback
    val foundation: PxFoundation
    val scale: PxTolerancesScale
    val physics: PxPhysics
    val cpuDispatcher: PxCpuDispatcher
    val stdFilterData: PxFilterData
    val cudaCtxMgr: PxCudaContextManager?
    val cudaEnabled: Boolean
    val stdMaterial: PxMaterial // TODO

    val actorTypeFlagsAll: PxActorTypeFlags
    val planeGeom: PxPlaneGeometry

    init {
        val version = PxTopLevelFunctions.getPHYSICS_VERSION()

        val versionMajor = version shr 24
        val versionMinor = (version shr 16) and 0xff
        val versionBuild = (version shr 8) and 0xff
        build = "v$versionMajor.$versionMinor.$versionBuild"

        numThreads =
            if (settings.numThreads < 0) clamp(Runtime.getRuntime().availableProcessors() - 2, 1, 16)
            else settings.numThreads

        allocator = PxDefaultAllocator()
        errorCb = PsErrorCallback(logger)
        foundation = PxTopLevelFunctions.CreateFoundation(version, allocator, errorCb)

        scale = PxTolerancesScale()
        physics = PxTopLevelFunctions.CreatePhysics(version, foundation, scale)

        cpuDispatcher = PxTopLevelFunctions.DefaultCpuDispatcherCreate(numThreads)
        stdFilterData = PxFilterData(
            1,    // collision group: 0 (i.e. 1 << 0)
            -0x1, // collision mask: collide with everything
            0,    // no additional collision flags
            0     // unused
        )

        cudaCtxMgr = /*if (settings.enableCuda) {
            val mgr = igUseMemory {
                val cudaCtxDesc = PxCudaContextManagerDesc()
                cudaCtxDesc.interopMode = PxCudaInteropModeEnum.NO_INTEROP
                PxCudaTopLevelFunctions.CreateCudaContextManager(foundation, cudaCtxDesc)
            }
            if (mgr == null || !mgr.contextIsValid()) {
                logger.warning("Could not create CUDA context")
                null
            } else {
                logger.info("Initialized CUDA ${mgr.deviceName} with ${mgr.deviceTotalMemBytes / 1024.0 / 1024.0} MB")
                mgr
            }
        } else*/ null
        cudaEnabled = cudaCtxMgr != null

        stdMaterial = physics.createMaterial(0.5f, 0.5f, 0f) // TODO

        actorTypeFlagsAll = PxActorTypeFlags((PxActorTypeFlag.DYNAMIC or PxActorTypeFlag.STATIC).toShort())
        planeGeom = PxPlaneGeometry()

        physicsThread.start()
    }

    override fun destroy() {
        physicsThread.destroy()

        spaces.forEach { (_, space) ->
            space.destroy()
        }
        spaces.clear()

        actorTypeFlagsAll.destroy()
        planeGeom.destroy()
        stdMaterial.release()
        physics.release()
        foundation.release()
        errorCb.destroy()
        allocator.destroy()
    }

    override fun runTask(task: Runnable) {
        physicsThread.execute(task)
    }

    fun MemoryStack.geometryOf(geometry: Geometry): PxGeometry {
        return when (geometry) {
            is SphereGeometry -> pxSphereGeometry(geometry.radius)
            is BoxGeometry -> pxBoxGeometry(geometry.halfExtent)
            is CapsuleGeometry -> pxCapsuleGeometry(geometry.radius, geometry.halfHeight)
            else -> throw IllegalArgumentException("Unsupported geometry type ${geometry::class.simpleName}")
        }
    }

    fun createShape(geometry: Geometry, transform: Transform): PxShape {
        val shape: PxShape
        useMemory {
            val pxGeometry = geometryOf(geometry)
            shape = physics.createShape(pxGeometry, stdMaterial, true)
            shape.localPose = pxTransform(transform)
        }
        shape.simulationFilterData = stdFilterData
        return shape
    }

    override fun createSpace(settings: PhysicsSpace.Settings): PhysicsSpace {
        val scene: PxScene
        useMemory {
            val desc = pxSceneDesc(scale)
            desc.cpuDispatcher = cpuDispatcher
            desc.filterShader = PxTopLevelFunctions.DefaultFilterShader()
            desc.gravity = pxVec3(settings.gravity)
            desc.flags.raise(PxSceneFlagEnum.eENABLE_ACTIVE_ACTORS)
//            if (cudaEnabled) {
//                desc.cudaContextManager = cudaCtxMgr
//                desc.flags.raise(PxSceneFlagEnum.eENABLE_GPU_DYNAMICS)
//                desc.broadPhaseType = PxBroadPhaseTypeEnum.eGPU
//                desc.gpuMaxNumPartitions = bSettings.gpuMaxPartitions
//                desc.gpuMaxNumStaticPartitions = bSettings.gpuMaxStaticPartitions
//            } else {
//                desc.broadPhaseType = bSettings.broadphaseType.px()
//            }
            scene = physics.createScene(desc)
        }

        val space = PsPhysicsSpace(this, scene)
        spaces[scene] = space
        return space
    }

    override fun destroySpace(space: PhysicsSpace) {
        space as PsPhysicsSpace
        spaces.remove(space.handle)
        space.destroy()
    }
}
