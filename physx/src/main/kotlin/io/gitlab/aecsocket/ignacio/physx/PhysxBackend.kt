package io.gitlab.aecsocket.ignacio.physx

import io.gitlab.aecsocket.ignacio.core.*
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import physx.PxTopLevelFunctions.*
import physx.common.*
import physx.physics.*
import java.util.logging.Logger
import kotlin.math.sqrt

class PhysxBackend(settings: Settings, logger: Logger) : IgBackend<PhysxBackend.Settings> {
    @ConfigSerializable
    data class Settings(
        val numThreads: Int = -1
    )

    var settings: Settings = settings
        private set
    val spaces = HashMap<Long, PhxPhysicsSpace>()

    val numThreads: Int

    val allocator: PxDefaultAllocator
    val errorCb: PxDefaultErrorCallback
    val foundation: PxFoundation
    val scale: PxTolerancesScale
    val physics: PxPhysics
    val cpuDispatcher: PxCpuDispatcher
    val stdFilterData: PxFilterData
    val stdMaterial: PxMaterial // TODO

    init {
        numThreads = if (settings.numThreads < 0) Runtime.getRuntime().availableProcessors() else settings.numThreads

        val version = getPHYSICS_VERSION()
        allocator = PxDefaultAllocator()
        errorCb = PxDefaultErrorCallback()
        foundation = CreateFoundation(version, allocator, errorCb)

        scale = PxTolerancesScale()
        physics = CreatePhysics(version, foundation, scale)

        cpuDispatcher = DefaultCpuDispatcherCreate(numThreads)
        stdFilterData = PxFilterData(
            1,    // collision group: 0 (i.e. 1 << 0)
            -0x1, // collision mask: collide with everything
            0,    // no additional collision flags
            0     // unused
        )
        stdMaterial = physics.createMaterial(0.5f, 0.5f, 0f) // TODO

        val versionMajor = version shr 24
        val versionMinor = (version shr 16) and 0xff
        val versionBuild = (version shr 8) and 0xff
        logger.info("Initialized PhysX v$versionMajor.$versionMinor.$versionBuild backend")
    }

    override fun reload(settings: Settings) {
        this.settings = settings
    }

    override fun createSpace(settings: IgPhysicsSpace.Settings): PhxPhysicsSpace {
        val handle: PxScene
        igUseMemory {
            val desc = pxSceneDesc(scale)
            desc.cpuDispatcher = cpuDispatcher
            desc.filterShader = DefaultFilterShader()
            desc.gravity = pxVec3(settings.gravity)
            handle = physics.createScene(desc)
        }

        val r = 1.0 / sqrt(2.0)
        val ground = createStaticBody(
            IgPlaneShape,
            Transform(Vec3(0.0, settings.groundPlaneY, 0.0), Quat(0.0, 0.0, r, r))
        )
        val space = PhxPhysicsSpace(this, handle, settings, ground)
        space.addBody(ground)
        spaces[handle.address] = space
        return space
    }

    override fun destroySpace(space: IgPhysicsSpace) {
        space as PhxPhysicsSpace
        spaces.remove(space.handle.address)

        space.bodies.forEach { (_, body) ->
            space.handle.removeActor(body.handle)
            body.destroy()
        }
        space.handle.release()
    }

    override fun createStaticBody(shape: IgShape, transform: Transform): PhxStaticBody {
        val phxShape: PxShape
        val phxBody: PxRigidStatic
        igUseMemory {
            val geom = pxGeometryOf(shape)
            phxShape = physics.createShape(geom, stdMaterial, true) // todo material
            phxShape.simulationFilterData = stdFilterData
            phxBody = physics.createRigidStatic(pxTransform(transform))
        }
        val body = PhxStaticBody(phxBody)
        body.attachShape(phxShape)
        return body
    }

    override fun createDynamicBody(shape: IgShape, transform: Transform, dynamics: IgBodyDynamics): PhxDynamicBody {
        val phxShape: PxShape
        val phxBody: PxRigidDynamic
        igUseMemory {
            val geom = pxGeometryOf(shape)
            phxShape = physics.createShape(geom, stdMaterial, true) // todo material
            phxShape.simulationFilterData = stdFilterData
            phxBody = physics.createRigidDynamic(pxTransform(transform))
        }
        phxBody.mass = dynamics.mass.toFloat()
        val body = PhxDynamicBody(phxBody)
        body.attachShape(phxShape)
        return body
    }

    override fun destroy() {
        spaces.forEach { (_, space) ->
            destroySpace(space)
        }

        stdMaterial.release()
        physics.release()
        foundation.release()
        errorCb.destroy()
        allocator.destroy()
    }
}
