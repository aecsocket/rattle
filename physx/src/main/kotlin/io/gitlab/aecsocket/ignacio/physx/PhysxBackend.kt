package io.gitlab.aecsocket.ignacio.physx

import io.gitlab.aecsocket.ignacio.core.*
import io.gitlab.aecsocket.ignacio.core.math.Transform
import io.gitlab.aecsocket.ignacio.core.math.Vec3
import org.lwjgl.system.MemoryStack
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import physx.PxTopLevelFunctions.*
import physx.common.*
import physx.geometry.PxGeometry
import physx.geometry.PxPlaneGeometry
import physx.physics.*
import java.util.logging.Logger

class PhysxBackend(
    settings: Settings,
    val physicsThread: IgPhysicsThread,
    logger: Logger
) : IgBackend<PhysxBackend.Settings> {
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

    private val planeGeom: PxPlaneGeometry

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

        planeGeom = PxPlaneGeometry()

        val versionMajor = version shr 24
        val versionMinor = (version shr 16) and 0xff
        val versionBuild = (version shr 8) and 0xff
        logger.info("Initialized PhysX v$versionMajor.$versionMinor.$versionBuild backend")
    }

    override fun reload(settings: Settings) {
        this.settings = settings
    }

    internal inline fun assertThread() = physicsThread.assertThread()

    override fun createSpace(settings: IgPhysicsSpace.Settings): PhxPhysicsSpace {
        assertThread()
        val handle: PxScene
        igUseMemory {
            val desc = pxSceneDesc(scale)
            desc.cpuDispatcher = cpuDispatcher
            desc.filterShader = DefaultFilterShader()
            desc.gravity = pxVec3(settings.gravity)
            handle = physics.createScene(desc)
        }

        val ground = createStaticBody(
            IgPlaneShape,
            Transform(Vec3(0.0, settings.groundPlaneY, 0.0), planeQuat)
        )
        val space = PhxPhysicsSpace(this, handle, settings, ground)
        space.addBody(ground)
        spaces[handle.address] = space
        return space
    }

    override fun destroySpace(space: IgPhysicsSpace) {
        assertThread()
        space as PhxPhysicsSpace
        spaces.remove(space.handle.address)

        space.bodies.forEach { (_, body) ->
            space.handle.removeActor(body.handle)
            body.destroy()
        }
        space.handle.release()
    }

    fun MemoryStack.pxGeometryOf(shape: IgShape): PxGeometry? {
        return when (shape) {
            is IgEmptyShape -> null
            is IgPlaneShape -> pxPlaneGeometry()
            is IgSphereShape -> pxSphereGeometry(shape.radius.toFloat())
            is IgBoxShape -> {
                val (hx, hy, hz) = shape.halfExtent
                pxBoxGeometry(hx.toFloat(), hy.toFloat(), hz.toFloat())
            }
        }
    }

    private fun <B : PxRigidActor> createBody(
        shape: IgShape,
        transform: Transform,
        factory: (PxTransform) -> B
    ): Pair<B, PxShape?> {
        val phxShape: PxShape?
        val phxBody: B
        igUseMemory {
            phxShape = pxGeometryOf(shape)?.let { geom ->
                physics.createShape(geom, stdMaterial, true).also { // todo material
                    it.simulationFilterData = stdFilterData
                }
            }
            phxBody = factory(pxTransform(transform))
        }
        return phxBody to phxShape
    }

    override fun createStaticBody(shape: IgShape, transform: Transform): PhxStaticBody {
        val (handle, pxShape) = createBody(shape, transform, physics::createRigidStatic)
        val body = PhxStaticBody(handle)
        pxShape?.let { body.attachShape(it) }
        return body
    }

    override fun createDynamicBody(shape: IgShape, transform: Transform, dynamics: IgBodyDynamics): PhxDynamicBody {
        val (handle, pxShape) = createBody(shape, transform, physics::createRigidDynamic)
        handle.mass = dynamics.mass.toFloat()
        val body = PhxDynamicBody(handle)
        pxShape?.let { body.attachShape(it) }
        return body
    }

    override fun step(spaces: Iterable<IgPhysicsSpace>) {
        spaces.forEach { space ->
            space as PhxPhysicsSpace
            space.handle.simulate(space.settings.stepInterval.toFloat())
        }

        spaces.forEach { space ->
            space as PhxPhysicsSpace
            space.handle.fetchResults(true)
        }
    }

    override fun destroy() {
        physicsThread.assertThread()
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
