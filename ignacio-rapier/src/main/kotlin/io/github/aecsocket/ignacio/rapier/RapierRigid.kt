package io.github.aecsocket.ignacio.rapier

import io.github.aecsocket.ignacio.FixedBody
import io.github.aecsocket.ignacio.Iso
import io.github.aecsocket.ignacio.RigidBody
import io.github.aecsocket.ignacio.VolumeAccess

@JvmInline
value class RigidBodyHandle(val id: Long)

sealed class RapierRigid<AR, AW>(
    val handle: RigidBodyHandle,
) : RigidBody<AR, AW>
        where AR : RapierRigid.Read<*>,
              AW : RapierRigid.Write<*, *> {

    sealed interface Read<VR : VolumeAccess> : RigidBody.Read<VR> {
        val body: rapier.dynamics.RigidBody

        override val position: Iso
            get() = pushArena { arena ->
                body.getPosition(arena).toIso()
            }
    }

    sealed interface Write<VR : VolumeAccess, VW : VR> : Read<VR>, RigidBody.Write<VR, VW> {
        override val body: rapier.dynamics.RigidBody.Mut

        override var position: Iso
            get() = super.position
            set(value) = pushArena { arena ->
                body.setPosition(value.toIsometry(arena), false)
            }
    }
}

/*
class PhysxFixed<VR : VolumeAccess, VW : VR>(
    override val body: PxRigidStatic,
    val volume: VW,
) : PhysxRigid<PhysxFixed<VR, VW>.Access, PhysxFixed<VR, VW>.Write>(body),
    FixedBody<PhysxFixed<VR, VW>.Access, PhysxFixed<VR, VW>.Write> {

    inner class Access : PhysxRigid.Access<VR>, FixedBody.Access<VR> {
        override val handle get() = this@PhysxFixed
        override val body get() = this@PhysxFixed.body
        override val volume get() = this@PhysxFixed.volume
    }

    inner class Write : PhysxRigid.Write<VR, VW>, FixedBody.Write<VR, VW> {
        override val handle get() = this@PhysxFixed
        override val body get() = this@PhysxFixed.body
        override val volume get() = this@PhysxFixed.volume
    }

    private val read = Access()
    private val write = Write()

    override fun <R> read(block: (Access) -> R) = block(read)

    override fun <R> write(block: (Write) -> R) = block(write)
}
 */

class RapierFixed<VR : VolumeAccess, VW : VR>(
    handle : RigidBodyHandle,
    val volume: VW,
) : RapierRigid<RapierFixed<VR, VW>.Read, RapierFixed<VR, VW>.Write>(handle),
    FixedBody<RapierFixed<VR, VW>.Read, RapierFixed<VR, VW>.Write> {

    inner class Read(
        override val body: rapier.dynamics.RigidBody,
    ) : RapierRigid.Read<VR>, FixedBody.Read<VR> {
        override val handle get() = this@RapierFixed
        override val volume get() = this@RapierFixed.volume
    }

    inner class Write(
        override val body: rapier.dynamics.RigidBody,
    ) : RapierRigid.Write<VR, VW>, FixedBody.Write<VR, VW> {
        override val handle get() = this@RapierFixed
        override val volume get() = this@RapierFixed.volume
    }
}
