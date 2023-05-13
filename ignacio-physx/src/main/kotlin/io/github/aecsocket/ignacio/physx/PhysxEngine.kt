package io.github.aecsocket.ignacio.physx

import io.github.aecsocket.ignacio.*
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import physx.common.PxDefaultAllocator
import physx.common.PxErrorCallbackImpl
import physx.common.PxErrorCodeEnum
import java.util.logging.Level
import java.util.logging.Logger

import physx.PxTopLevelFunctions.*
import physx.common.PxCpuDispatcher
import physx.common.PxErrorCallback
import physx.common.PxFoundation
import physx.common.PxTolerancesScale
import physx.geometry.PxBoxGeometry
import physx.geometry.PxCapsuleGeometry
import physx.geometry.PxSphereGeometry
import physx.physics.PxMaterial
import physx.physics.PxPhysics
import physx.physics.PxSceneDesc
import physx.physics.PxShapeFlagEnum
import physx.physics.PxShapeFlags

class PhysxEngine(
    logger: Logger,
    settings: Settings,
) : IgnacioEngine {
    @ConfigSerializable
    data class Settings(
        val numThreads: Int = 0,
    )

    val allocator: PxDefaultAllocator
    val errorCallback: PxErrorCallback
    val foundation: PxFoundation
    val tolerances: PxTolerancesScale
    val physics: PxPhysics
    val dispatcher: PxCpuDispatcher

    val defaultMaterial: PxMaterial
    val defaultShapeFlags: PxShapeFlags

    init {
        val version = getPHYSICS_VERSION()

        allocator = PxDefaultAllocator()
        errorCallback = object : PxErrorCallbackImpl() {
            override fun reportError(code: PxErrorCodeEnum, message: String, file: String, line: Int) {
                val level = when (code) {
                    PxErrorCodeEnum.eNO_ERROR, PxErrorCodeEnum.eMASK_ALL -> Level.INFO

                    PxErrorCodeEnum.eDEBUG_INFO -> Level.FINE

                    PxErrorCodeEnum.eDEBUG_WARNING, PxErrorCodeEnum.ePERF_WARNING -> Level.WARNING

                    PxErrorCodeEnum.eINVALID_PARAMETER,
                    PxErrorCodeEnum.eINVALID_OPERATION,
                    PxErrorCodeEnum.eOUT_OF_MEMORY,
                    PxErrorCodeEnum.eINTERNAL_ERROR,
                    PxErrorCodeEnum.eABORT -> Level.SEVERE
                }
                logger.log(level, "$message ($file:$line)")
            }
        }
        foundation = CreateFoundation(version, allocator, errorCallback)

        tolerances = PxTolerancesScale()
        physics = CreatePhysics(version, foundation, tolerances)

        dispatcher = DefaultCpuDispatcherCreate(numThreads(settings.numThreads))

        // TODO
        defaultMaterial = physics.createMaterial(0.5f, 0.5f, 0.5f)
        defaultShapeFlags = PxShapeFlags(
            (PxShapeFlagEnum.eSCENE_QUERY_SHAPE.value or PxShapeFlagEnum.eSIMULATION_SHAPE.value).toByte()
        )
    }

    override fun createShape(geom: Geometry): Shape {
        val handle = pushArena { arena ->
            physics.createShape(when (geom) {
                is Sphere -> PxSphereGeometry.createAt(
                    arena,
                    arena.alloc,
                    geom.radius.toFloat(),
                )
                is Cuboid -> PxBoxGeometry.createAt(
                    arena,
                    arena.alloc,
                    geom.halfExtent.x.toFloat(),
                    geom.halfExtent.y.toFloat(),
                    geom.halfExtent.z.toFloat(),
                )
                is Capsule -> PxCapsuleGeometry.createAt(
                    arena,
                    arena.alloc,
                    geom.radius.toFloat(),
                    geom.halfHeight.toFloat(),
                )
            }, defaultMaterial)
        }
        return PhysxShape(handle)
    }

    override fun createSpace(settings: PhysicsSpace.Settings): PhysicsSpace {
        val scene = pushArena { arena ->
            val desc = PxSceneDesc.createAt(arena, arena.alloc, tolerances)
            desc.gravity = settings.gravity.asNative(arena)
            desc.cpuDispatcher = dispatcher
            desc.filterShader = DefaultFilterShader()
            physics.createScene(desc)
        }
        return PhysxSpace()
    }
}