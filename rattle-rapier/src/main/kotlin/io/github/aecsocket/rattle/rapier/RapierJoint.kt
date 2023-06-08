package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.rattle.*
import rapier.data.ArenaKey
import rapier.dynamics.joint.GenericJoint

@JvmInline
value class RapierImpulseJointKey(val id: Long) : ImpulseJointKey {
    override fun toString(): String = ArenaKey.asString(id)
}

sealed class RapierJoint(
    override val handle: GenericJoint,
    override var space: RapierSpace?,
) : RapierNative(), RapierPhysicsNative, Joint {
    override val localFrameA: Iso
        get() = pushArena { arena ->
            handle.getLocalFrame1(arena).toIso()
        }

    override val localFrameB: Iso
        get() = pushArena { arena ->
            handle.getLocalFrame2(arena).toIso()
        }

    override val localAxisA: Vec
        get() = pushArena { arena ->
            handle.getLocalAxis1(arena).toVec()
        }

    override val localAxisB: Vec
        get() = pushArena { arena ->
            handle.getLocalAxis2(arena).toVec()
        }

    override val localAnchorA: Vec
        get() = pushArena { arena ->
            handle.getLocalAnchor1(arena).toVec()
        }

    override val localAnchorB: Vec
        get() = pushArena { arena ->
            handle.getLocalAnchor2(arena).toVec()
        }

    override val contactsEnabled: Boolean
        get() = handle.contactsEnabled

    class Read internal constructor(
        handle: GenericJoint,
        space: RapierSpace?,
    ) : RapierJoint(handle, space) {
        override val nativeType get() = "RapierJoint.Read"
    }

    class Write internal constructor(
        override val handle: GenericJoint.Mut,
        space: RapierSpace?,
    ) : RapierJoint(handle, space), Joint.Own {
        override val nativeType get() = "RapierJoint.Write"

        override fun localFrameA(value: Iso): Write {
            pushArena { arena ->
                handle.setLocalFrame1(value.toIsometry(arena))
            }
            return this
        }

        override fun localFrameB(value: Iso): Write {
            pushArena { arena ->
                handle.setLocalFrame2(value.toIsometry(arena))
            }
            return this
        }

        override fun localAxisA(value: Vec): Write {
            pushArena { arena ->
                handle.setLocalAxis1(value.toVector(arena))
            }
            return this
        }

        override fun localAxisB(value: Vec): Write {
            pushArena { arena ->
                handle.setLocalAxis2(value.toVector(arena))
            }
            return this
        }

        override fun localAnchorA(value: Vec): Write {
            pushArena { arena ->
                handle.setLocalAnchor1(value.toVector(arena))
            }
            return this
        }

        override fun localAnchorB(value: Vec): Write {
            pushArena { arena ->
                handle.setLocalAnchor2(value.toVector(arena))
            }
            return this
        }

        override fun contactsEnabled(value: Boolean): Write {
            handle.contactsEnabled = value
            return this
        }
    }
}

sealed class RapierImpulseJoint(
    override val handle: rapier.dynamics.joint.impulse.ImpulseJoint,
) : RapierNative(), ImpulseJoint {
    override val bodyA: RigidBodyKey
        get() = RapierRigidBodyKey(handle.body1)

    override val bodyB: RigidBodyKey
        get() = RapierRigidBodyKey(handle.body2)

    override val translationImpulses: Vec
        get() = pushArena { arena ->
            handle.getImpulses(arena).run { Vec(x, y, z) }
        }

    override val rotationImpulses: Vec
        get() = pushArena { arena ->
            handle.getImpulses(arena).run { Vec(w, a, b) }
        }

    class Read internal constructor(
        handle: rapier.dynamics.joint.impulse.ImpulseJoint,
        space: RapierSpace?,
    ) : RapierImpulseJoint(handle), Joint by RapierJoint.Read(handle.data, space) {
        override val nativeType get() = "RapierJoint.Read"
    }

    class Write internal constructor(
        override val handle: rapier.dynamics.joint.impulse.ImpulseJoint.Mut,
        space: RapierSpace?,
    ) : RapierImpulseJoint(handle), ImpulseJoint.Mut, Joint.Mut by RapierJoint.Write(handle.data, space) {
        override val nativeType get() = "RapierJoint.Write"

        override fun bodyA(value: RigidBodyKey): Write {
            value as RapierRigidBodyKey
            handle.body1 = value.id
            return this
        }

        override fun bodyB(value: RigidBodyKey): Write {
            value as RapierRigidBodyKey
            handle.body2 = value.id
            return this
        }
    }
}
