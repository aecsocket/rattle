package io.gitlab.aecsocket.ignacio.physx

import io.gitlab.aecsocket.ignacio.core.*
import io.gitlab.aecsocket.ignacio.core.math.Transform
import physx.physics.PxRigidActor
import physx.physics.PxRigidDynamic
import physx.physics.PxRigidStatic
import physx.physics.PxShape

open class PhxBody(open val handle: PxRigidActor) : IgBody {
    override var transform: Transform
        get() = handle.globalPose.ig()
        set(value) {
            igUseMemory {
                val tf = pxTransform(pxVec3(value.position), pxQuat(value.rotation))
                handle.setGlobalPose(tf, false)
            }
        }

    val shapes = HashMap<Long, PxShape>()

    fun attachShape(shape: PxShape) {
        shapes[shape.address] = shape
        handle.attachShape(shape)
    }

    fun detachShape(shape: PxShape) {
        shapes.remove(shape.address)
        handle.detachShape(shape)
    }

    override fun destroy() {
        shapes.forEach { (_, shape) ->
            shape.release()
        }
        handle.release()
    }
}

class PhxStaticBody(override val handle: PxRigidStatic) : PhxBody(handle), IgStaticBody {
}

class PhxDynamicBody(override val handle: PxRigidDynamic) : PhxBody(handle), IgDynamicBody {
    override val sleeping: Boolean
        get() = handle.isSleeping

    override fun wake() {
        handle.wakeUp()
    }
}
