package io.github.aecsocket.ignacio

data class ColliderChild(
    val shape: Shape,
    val position: Iso,
)

sealed interface ColliderDesc {
    data class Single(
        val shape: Shape,
    ) : ColliderDesc

    data class FixedCompound(
        val children: Collection<ColliderChild>,
    ) : ColliderDesc

    data class MutableCompound(
        val children: Collection<ColliderChild>,
    ) : ColliderDesc
}

sealed interface Collider<CR : Collider<CR, CW>, CW : CR> {
    interface Single : Collider<Single, Single.Write> {
        operator fun invoke(): Shape

        interface Write : Single {
            operator fun invoke(shape: Shape)
        }
    }

    interface FixedCompound : Collider<FixedCompound, FixedCompound> {
        // todo iterate
    }

    interface MutableCompound : Collider<MutableCompound, MutableCompound.Write> {
        // todo iterate

        interface Write : MutableCompound {
            // todo
        }
    }
}

sealed interface RigidBodyHandle<AR : RigidBodyHandle.Read<*>, AW : RigidBodyHandle.Write<*, *>> {
    fun <R> read(block: (AR) -> R): R

    fun <R> write(block: (AW) -> R): R

    interface Access<CR : Collider<CR, *>> {
        val handle: RigidBodyHandle<Read<CR>, *>

        val collider: CR

        val position: Iso
    }

    interface Read<CR : Collider<CR, *>> : Access<CR>

    interface Write<CR : Collider<CR, CW>, CW : CR> : Access<CR> {
        override val handle: RigidBodyHandle<Read<CR>, Write<CR, CW>>

        override val collider: CW

        override var position: Iso
    }
}

interface FixedBodyHandle<CR : Collider<CR, CW>, CW : CR> : RigidBodyHandle<FixedBodyHandle.Read<CR>, FixedBodyHandle.Write<CR, CW>> {
    interface Access<CR : Collider<CR, *>> : RigidBodyHandle.Access<CR>

    interface Read<CR : Collider<CR, *>> : Access<CR>, RigidBodyHandle.Read<CR>

    interface Write<CR : Collider<CR, CW>, CW : CR> : Access<CR>, RigidBodyHandle.Write<CR, CW>
}

interface MovingBodyHandle<CR : Collider<CR, CW>, CW : CR> : RigidBodyHandle<FixedBodyHandle.Read<CR>, FixedBodyHandle.Write<CR, CW>> {
    interface Access<CR : Collider<CR, *>> : RigidBodyHandle.Access<CR> {
        val isSleeping: Boolean
    }

    interface Read<CR : Collider<CR, *>> : Access<CR>, RigidBodyHandle.Read<CR>

    interface Write<CR : Collider<CR, CW>, CW : CR> : Access<CR>, RigidBodyHandle.Write<CR, CW> {
        override var isSleeping: Boolean
    }
}

typealias RigidBody = RigidBodyHandle<*, *>
typealias FixedBody = FixedBodyHandle<*, *>
typealias MovingBody = MovingBodyHandle<*, *>
