package io.gitlab.aecsocket.ignacio.physx

import io.gitlab.aecsocket.ignacio.core.*
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import physx.PxTopLevelFunctions.*
import physx.common.PxCpuDispatcher
import physx.common.PxDefaultAllocator
import physx.common.PxDefaultErrorCallback
import physx.common.PxFoundation
import physx.common.PxTolerancesScale
import physx.physics.PxMaterial
import physx.physics.PxPhysics
import physx.physics.PxRigidDynamic
import physx.physics.PxRigidStatic
import physx.physics.PxScene
import physx.physics.PxShape
import java.io.File
import java.util.logging.Logger

class PhxBackend(root: File, settings: Settings, logger: Logger) : IgnacioBackend {
    @ConfigSerializable
    data class Settings(
        val numThreads: Int = -1
    )

    val numThreads: Int

    val foundation: PxFoundation
    val scale: PxTolerancesScale
    val physics: PxPhysics
    val cpuDispatcher: PxCpuDispatcher
    val stdMaterial: PxMaterial // TODO

    init {
        numThreads = if (settings.numThreads < 0) Runtime.getRuntime().availableProcessors() else settings.numThreads

        val version = getPHYSICS_VERSION()
        val allocator = PxDefaultAllocator()
        val errorCb = PxDefaultErrorCallback()
        foundation = CreateFoundation(version, allocator, errorCb)

        scale = PxTolerancesScale()
        physics = CreatePhysics(version, foundation, scale)

        cpuDispatcher = DefaultCpuDispatcherCreate(numThreads)
        stdMaterial = physics.createMaterial(0.5f, 0.5f, 0.5f) // TODO

        val versionMajor = version shr 24
        val versionMinor = (version shr 16) and 0xff
        val versionBuild = (version shr 8) and 0xff
        logger.info("Initialized PhysX v$versionMajor.$versionMinor.$versionBuild backend")
    }

    override fun createSpace(settings: IgSpaceSettings): IgPhysicsSpace {
        val scene: PxScene
        igUseMemory {
            val desc = pxSceneDesc(scale)
            desc.cpuDispatcher = cpuDispatcher
            desc.filterShader = DefaultFilterShader()
            desc.gravity = pxVec3(settings.gravity)
            scene = physics.createScene(desc)
        }
        return PhxPhysicsSpace(scene)
    }

    override fun createStaticBody(shape: IgShape, transform: Transform): IgStaticBody {
        val phxShape: PxShape
        val phxBody: PxRigidStatic
        igUseMemory {
            val geom = pxGeometryOf(shape)
            phxShape = physics.createShape(geom, stdMaterial, true) // todo material
            phxBody = physics.createRigidStatic(pxTransform(transform))
        }
        phxBody.attachShape(phxShape)
        return PhxStaticBody(phxBody)
    }

    override fun createDynamicBody(shape: IgShape, transform: Transform, dynamics: IgBodyDynamics): IgDynamicBody {
        val phxShape: PxShape
        val phxBody: PxRigidDynamic
        igUseMemory {
            val geom = pxGeometryOf(shape)
            phxShape = physics.createShape(geom, stdMaterial, true) // todo material
            phxBody = physics.createRigidDynamic(pxTransform(transform))
        }
        phxBody.mass = dynamics.mass.toFloat()
        phxBody.attachShape(phxShape)
        return PhxDynamicBody(phxBody)
    }
}
