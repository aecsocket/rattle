package io.gitlab.aecsocket.ignacio.physx

import io.gitlab.aecsocket.ignacio.core.*
import physx.physics.PxRigidActor
import physx.physics.PxRigidDynamic
import physx.physics.PxRigidStatic

open class PhxBody(open val handle: PxRigidActor) : IgBody {
    override var transform: Transform
        get() = handle.globalPose.ig()
        set(value) {
            igUseMemory {
                val tf = pxTransform(pxVec3(value.position), pxQuat(value.rotation))
                handle.setGlobalPose(tf, false)
            }
        }
}

class PhxStaticBody(override val handle: PxRigidStatic) : PhxBody(handle), IgStaticBody {
}

class PhxDynamicBody(override val handle: PxRigidDynamic) : PhxBody(handle), IgDynamicBody {
}
