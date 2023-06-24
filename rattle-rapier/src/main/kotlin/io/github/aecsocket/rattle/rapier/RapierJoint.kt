package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.klam.*
import io.github.aecsocket.rattle.*
import rapier.data.ArenaKey
import rapier.dynamics.joint.GenericJoint
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or

@JvmInline
value class RapierImpulseJointKey(val id: Long) : ImpulseJointKey {
    override fun toString(): String = ArenaKey.asString(id)
}

private fun Array<out Dof>.bitMask() = (fold(0) { acc, layer -> acc or (1 shl layer.ordinal) }).toByte()

sealed class RapierJointAxis(
    override val handle: GenericJoint,
    val axis: rapier.dynamics.joint.JointAxis,
) : RapierNative(), JointAxis {
    val axisBit = (1 shl axis.ordinal).toByte()

    // the order that we get `state` in is a bit arbitrary here
    // we will prioritise `limitAxes`, since if the bit is set for our axis,
    // it means it doesn't support SIMD processing, which may mean the joint processes slower
    override val state: JointAxis.State
        get() {
            handle.getLimits(axis)?.let { limits ->
                return JointAxis.State.Limited(
                    min = limits.min,
                    max = limits.max,
                    impulse = limits.impulse,
                )
            }
            return when {
                (handle.lockedAxes and axisBit) != 0.toByte() -> JointAxis.State.Locked
                else -> JointAxis.State.Free
            }
        }

    override val motor: JointAxis.Motor
        get() {
            val motor = handle.getMotor(axis) ?: return JointAxis.Motor.Disabled
            return JointAxis.Motor.Enabled(
                targetVel = motor.targetVel,
                targetPos = motor.targetPos,
                stiffness = motor.stiffness,
                damping = motor.damping,
                maxForce = motor.maxForce,
                impulse = motor.impulse,
                model = motor.model.toRattle(),
            )
        }

    class Read internal constructor(
        handle: GenericJoint,
        axis: rapier.dynamics.joint.JointAxis,
    ) : RapierJointAxis(handle, axis) {
        override val nativeType get() = "RapierJointAxis.Read"
    }

    class Write internal constructor(
        override val handle: GenericJoint.Mut,
        axis: rapier.dynamics.joint.JointAxis,
    ) : RapierJointAxis(handle, axis), JointAxis.Mut {
        override val nativeType get() = "RapierJointAxis.Write"

        private fun set(from: Byte) = from or axisBit

        private fun clear(from: Byte) = from and (axisBit.inv())

        override fun state(value: JointAxis.State): Write {
            when (value) {
                is JointAxis.State.Free -> {
                    handle.lockedAxes = clear(handle.lockedAxes)
                    handle.limitAxes = clear(handle.limitAxes)
                }
                is JointAxis.State.Limited -> {
                    handle.lockedAxes = clear(handle.lockedAxes)
                    handle.setLimits(axis, value.min, value.max)
                    // TODO oops I forgot to make bindings for the `limits` array!
                }
                is JointAxis.State.Locked -> {
                    handle.lockedAxes = set(handle.lockedAxes)
                    handle.limitAxes = clear(handle.limitAxes)
                }
            }
            return this
        }

        override fun motor(value: JointAxis.Motor): Write {
            when (value) {
                is JointAxis.Motor.Disabled -> {
                    handle.motorAxes = clear(handle.motorAxes)
                }
                is JointAxis.Motor.Enabled -> {
                    handle.motorAxes = set(handle.motorAxes)
                    // TODO oops I forgot to make bindings for the `motor` array!
                }
            }
            return this
        }
    }
}

sealed class RapierJoint(
    override val handle: GenericJoint,
    override var space: RapierSpace?,
) : RapierNative(), RapierPhysicsNative, Joint {
    override val localFrameA: DIso3
        get() = handle.localFrame1.toIso()

    override val localFrameB: DIso3
        get() = handle.localFrame2.toIso()

    override val localAxisA: DVec3
        get() = handle.localAxis1.toVec()

    override val localAxisB: DVec3
        get() = handle.localAxis2.toVec()

    override val localAnchorA: DVec3
        get() = handle.localAnchor1.toVec()

    override val localAnchorB: DVec3
        get() = handle.localAnchor2.toVec()

    override val contactsEnabled: Boolean
        get() = handle.contactsEnabled

    private fun wrapAxis(axis: rapier.dynamics.joint.JointAxis): JointAxis =
        RapierJointAxis.Read(handle, axis)

    override val x = wrapAxis(rapier.dynamics.joint.JointAxis.X)
    override val y = wrapAxis(rapier.dynamics.joint.JointAxis.Y)
    override val z = wrapAxis(rapier.dynamics.joint.JointAxis.Z)
    override val angX = wrapAxis(rapier.dynamics.joint.JointAxis.ANG_X)
    override val angY = wrapAxis(rapier.dynamics.joint.JointAxis.ANG_Y)
    override val angZ = wrapAxis(rapier.dynamics.joint.JointAxis.ANG_Z)

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

        private fun wrapAxis(axis: rapier.dynamics.joint.JointAxis): JointAxis.Mut =
            RapierJointAxis.Write(handle, axis)

        override val x = wrapAxis(rapier.dynamics.joint.JointAxis.X)
        override val y = wrapAxis(rapier.dynamics.joint.JointAxis.Y)
        override val z = wrapAxis(rapier.dynamics.joint.JointAxis.Z)
        override val angX = wrapAxis(rapier.dynamics.joint.JointAxis.ANG_X)
        override val angY = wrapAxis(rapier.dynamics.joint.JointAxis.ANG_Y)
        override val angZ = wrapAxis(rapier.dynamics.joint.JointAxis.ANG_Z)

        override fun localFrameA(value: DIso3): Write {
            handle.localFrame1 = value.toIsometry()
            return this
        }

        override fun localFrameB(value: DIso3): Write {
            handle.localFrame2 = value.toIsometry()
            return this
        }

        override fun localAxisA(value: DVec3): Write {
            handle.localAxis1 = value.toVector()
            return this
        }

        override fun localAxisB(value: DVec3): Write {
            handle.localAxis2 = value.toVector()
            return this
        }

        override fun localAnchorA(value: DVec3): Write {
            handle.localAnchor1 = value.toVector()
            return this
        }

        override fun localAnchorB(value: DVec3): Write {
            handle.localAnchor2 = value.toVector()
            return this
        }

        override fun contactsEnabled(value: Boolean): Write {
            handle.contactsEnabled = value
            return this
        }

        override fun lockAll(vararg degrees: Dof): Write {
            // why is `Byte.or` experimental? come on
            handle.lockedAxes = handle.lockedAxes or degrees.bitMask()
            return this
        }

        override fun freeAll(vararg degrees: Dof): Write {
            handle.lockedAxes = handle.lockedAxes and (degrees.bitMask().inv())
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

    override val translationImpulses: DVec3
        get() = handle.impulses.run { DVec3(x, y, z) }

    override val rotationImpulses: DVec3
        get() = handle.impulses.run { DVec3(w, a, b) }

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
