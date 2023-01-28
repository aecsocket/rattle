package io.gitlab.aecsocket.ignacio.physx

import io.gitlab.aecsocket.ignacio.core.*
import io.gitlab.aecsocket.ignacio.core.math.Transform
import io.gitlab.aecsocket.ignacio.core.math.Vec3
import physx.physics.PxRigidActor
import physx.physics.PxRigidDynamic
import physx.physics.PxRigidStatic
import physx.physics.PxShape

data class PhxShape(
    val handle: PxShape,
    override val geometry: IgGeometry,
    override val transform: Transform
) : IgShape {
    override fun destroy() {
        handle.release()
    }
}

sealed class PhxBody(
    protected val backend: PhysxBackend,
    open val handle: PxRigidActor
) : IgBody {
    override var transform: Transform
        get() {
            assertThread()
            return handle.globalPose.ig()
        }
        set(value) {
            igUseMemory {
                val tf = pxTransform(pxVec3(value.position), pxQuat(value.rotation))
                handle.setGlobalPose(tf, false)
            }
        }

    val mShapes = HashMap<Long, PhxShape>()
    override val shapes get() = mShapes.values

    internal inline fun assertThread() = backend.assertThread()

    override fun isAdded() = handle.scene != null

    override fun setGeometry(geometry: IgGeometry) {
        assertThread()
        detachAllShapes()
        igUseMemory {
            attachShape(backend.createShape(geometry))
        }
    }

    override fun attachShape(shape: IgShape) {
        assertThread()
        shape as PhxShape
        mShapes[shape.handle.address] = shape
        handle.attachShape(shape.handle)
    }

    override fun detachShape(shape: IgShape) {
        assertThread()
        shape as PhxShape
        mShapes.remove(shape.handle.address)
        handle.detachShape(shape.handle)
    }

    override fun detachAllShapes() {
        assertThread()
        mShapes.forEach { (_, shape) ->
            handle.detachShape(shape.handle)
        }
        mShapes.clear()
    }

    override fun destroy() {
        assertThread()
        mShapes.forEach { (_, shape) ->
            shape.handle.release()
        }
        handle.release()
    }
}

class PhxStaticBody(
    backend: PhysxBackend,
    override val handle: PxRigidStatic
) : PhxBody(backend, handle), IgStaticBody {
}

class PhxDynamicBody(
    backend: PhysxBackend,
    override val handle: PxRigidDynamic
) : PhxBody(backend, handle), IgDynamicBody {
    override var linearVelocity: Vec3
        get() {
            assertThread()
            return handle.linearVelocity.ig()
        }
        set(value) {
            assertThread()
            igUseMemory {
                handle.linearVelocity = pxVec3(value)
            }
        }

    override var angularVelocity: Vec3
        get() {
            assertThread()
            return handle.angularVelocity.ig()
        }
        set(value) {
            assertThread()
            igUseMemory {
                handle.angularVelocity = pxVec3(value)
            }
        }

    override val sleeping: Boolean
        get() = handle.isSleeping

    override fun wake() {
        handle.wakeUp()
    }
}
