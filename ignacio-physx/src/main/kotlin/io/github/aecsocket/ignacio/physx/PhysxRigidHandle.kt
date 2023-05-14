package io.github.aecsocket.ignacio.physx

import io.github.aecsocket.ignacio.*
import physx.physics.PxRigidActor
import physx.physics.PxRigidStatic

sealed class PhysxRigidHandle<AR : PhysxRigidHandle.Access<*>, AW: PhysxRigidHandle.Write<*, *>>(
    open val body: PxRigidActor,
) : RigidBodyHandle<AR, AW> {
    sealed interface Access<VR : VolumeAccess> : RigidBodyHandle.Access<VR> {
        val body: PxRigidStatic

        override val position: Iso
            get() = body.globalPose.toIso()
    }

    sealed interface Write<VR : VolumeAccess, VW : VR> : Access<VR>, RigidBodyHandle.Write<VR, VW> {
        override var position: Iso
            get() = super.position
            set(value) = pushArena { arena ->
                body.globalPose = value.toPx(arena)
            }
    }
}

class PhysxFixedHandle<VR : VolumeAccess, VW : VR>(
    override val body: PxRigidStatic,
) : PhysxRigidHandle<PhysxFixedHandle.Access<VR>, PhysxFixedHandle.Write<VW>>(body), FixedBodyHandle<VR, VW> {
    sealed interface Access<VR : VolumeAccess> : PhysxRigidHandle.Access<VR>, FixedBodyHandle.Access<VR> {

    }

    sealed interface Write<VR : VolumeAccess, VW : VR> : PhysxRigidHandle.Write<VR, VW>, FixedBodyHandle.Write<VR, VW> {

    }
}
