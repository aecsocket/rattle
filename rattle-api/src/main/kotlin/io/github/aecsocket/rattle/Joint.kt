package io.github.aecsocket.rattle

sealed interface AxisState {
    /* TODO: Kotlin 1.9 data */ object Free : AxisState

    data class Limited(
        val min: Real,
        val max: Real,
    ) : AxisState

    /* TODO: Kotlin 1.9 data */ object Locked : AxisState
}

enum class JointAxis {
    X,
    Y,
    Z,
    ANG_X,
    ANG_Y,
    ANG_Z,
}

data class JointAxes(
    val x: AxisState = AxisState.Free,
    val y: AxisState = AxisState.Free,
    val z: AxisState = AxisState.Free,
    val angX: AxisState = AxisState.Free,
    val angY: AxisState = AxisState.Free,
    val angZ: AxisState = AxisState.Free,
) : Iterable<JointAxes.Entry> {
    data class Entry(
        val axis: JointAxis,
        val state: AxisState,
    )

    override fun iterator() = object : Iterator<Entry> {
        var cursor = 0

        override fun hasNext() = cursor < 5

        override fun next(): Entry {
            return when (cursor) {
                0 -> Entry(JointAxis.X, x)
                1 -> Entry(JointAxis.Y , y)
                2 -> Entry(JointAxis.Z , z)
                3 -> Entry(JointAxis.ANG_X , angX)
                4 -> Entry(JointAxis.ANG_Y , angY)
                5 -> Entry(JointAxis.ANG_Z , angZ)
                else -> throw NoSuchElementException()
            }.also { cursor += 1 }
        }
    }
}

/**
 * A key used to index into a [PhysicsSpace] to gain a reference, mutable or immutable, to an [ImpulseJoint].
 */
interface ImpulseJointKey

/**
 * A key used to index into a [PhysicsSpace] to gain a reference, mutable or immutable, to a [MultibodyJoint].
 */
interface MultibodyJointKey

interface Joint {
    val localFrameA: Iso

    val localFrameB: Iso

    val localAxisA: Vec

    val localAxisB: Vec

    val localAnchorA: Vec

    val localAnchorB: Vec

    val contactsEnabled: Boolean

    interface Mut : Joint {
        fun localFrameA(value: Iso): Mut

        fun localFrameB(value: Iso): Mut

        fun localAxisA(value: Vec): Mut

        fun localAxisB(value: Vec): Mut

        fun localAnchorA(value: Vec): Mut

        fun localAnchorB(value: Vec): Mut

        fun contactsEnabled(value: Boolean): Mut
    }

    interface Own : Mut {
        override fun localFrameA(value: Iso): Own

        override fun localFrameB(value: Iso): Own

        override fun localAxisA(value: Vec): Own

        override fun localAxisB(value: Vec): Own

        override fun localAnchorA(value: Vec): Own

        override fun localAnchorB(value: Vec): Own

        override fun contactsEnabled(value: Boolean): Own
    }
}

interface ImpulseJoint : Joint {
    val bodyA: RigidBodyKey

    val bodyB: RigidBodyKey

    val translationImpulses: Vec

    val rotationImpulses: Vec

    interface Mut : ImpulseJoint, Joint.Mut {
        fun bodyA(value: RigidBodyKey): Mut

        fun bodyB(value: RigidBodyKey): Mut
    }
}

interface MultibodyJoint : Joint {
    interface Mut : MultibodyJoint, Joint.Mut {

    }
}
