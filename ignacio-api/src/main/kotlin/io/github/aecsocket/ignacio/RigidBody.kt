package io.github.aecsocket.ignacio

interface RigidBodyHandle<AR : RigidBodyHandle.Access<*>, AW : RigidBodyHandle.Write<*, *>> {
    fun <R> read(block: (AR) -> R): R

    fun <R> write(block: (AW) -> R): R

    interface Access<VR : VolumeAccess> {
        val handle: RigidBodyHandle<Access<VR>, *>

        val collider: VR

        val position: Iso
    }

    interface Write<VR : VolumeAccess, VW : VR> : Access<VR> {
        override val handle: RigidBodyHandle<Access<VR>, Write<VR, VW>>

        override val collider: VW

        override var position: Iso
    }
}

interface FixedBodyHandle<VR : VolumeAccess, VW : VR> : RigidBodyHandle<FixedBodyHandle.Access<VR>, FixedBodyHandle.Write<VR, VW>> {
    interface Access<VR : VolumeAccess> : RigidBodyHandle.Access<VR>

    interface Write<VR : VolumeAccess, VW : VR> : Access<VR>, RigidBodyHandle.Write<VR, VW>
}

interface MovingBodyHandle<VR : VolumeAccess, VW : VR> : RigidBodyHandle<MovingBodyHandle.Access<VR>, MovingBodyHandle.Write<VR, VW>> {
    interface Access<VR : VolumeAccess> : RigidBodyHandle.Access<VR> {
        val isSleeping: Boolean

        val linearVelocity: Vec
    }

    interface Write<VR : VolumeAccess, VW : VR> : Access<VR>, RigidBodyHandle.Write<VR, VW> {
        override var isSleeping: Boolean

        override var linearVelocity: Vec
    }
}

typealias RigidBody = RigidBodyHandle<*, *>
typealias FixedBody = FixedBodyHandle<*, *>
typealias MovingBody = MovingBodyHandle<*, *>
