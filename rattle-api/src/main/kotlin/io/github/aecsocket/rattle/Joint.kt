package io.github.aecsocket.rattle

/**
 * A key used to index into a [PhysicsSpace] to gain a reference, mutable or immutable, to an [ImpulseJoint].
 */
interface ImpulseJointKey

interface JointAxis {
    val state: State

    val motor: Motor

    interface Mut : JointAxis {
        fun state(value: State): Mut

        fun motor(value: Motor): Mut
    }

    sealed interface State {
        /* TODO: Kotlin 1.9 data */ object Free : State

        data class Limited(
            val min: Real,
            val max: Real,
            val impulse: Real,
        ) : State

        /* TODO: Kotlin 1.9 data */ object Locked : State
    }

    sealed interface Motor {
        /* TODO: Kotlin 1.9 data */ object Disabled : Motor

        data class Enabled(
            val targetVel: Real,
            val targetPos: Real,
            val stiffness: Real,
            val damping: Real,
            val maxForce: Real,
            val impulse: Real,
            val model: Model,
        ) : Motor {
            init {
                // TODO there's probably more requirements here
                require(impulse >= 0.0) { "requires impulse >= 0.0" }
            }
        }

        enum class Model {
            ACCELERATION_BASED,
            FORCE_BASED,
        }
    }
}

interface Joint {
    val localFrameA: Iso

    val localFrameB: Iso

    // these four fields are just accessors into a slice of the localFrame effectively
    // but they're more convenient to use
    val localAxisA: Vec

    val localAxisB: Vec

    val localAnchorA: Vec

    val localAnchorB: Vec

    val contactsEnabled: Boolean

    val x: JointAxis

    val y: JointAxis

    val z: JointAxis

    val angX: JointAxis

    val angY: JointAxis

    val angZ: JointAxis

    interface Mut : Joint {
        override val x: JointAxis.Mut

        override val y: JointAxis.Mut

        override val z: JointAxis.Mut

        override val angX: JointAxis.Mut

        override val angY: JointAxis.Mut

        override val angZ: JointAxis.Mut

        fun localFrameA(value: Iso): Mut

        fun localFrameB(value: Iso): Mut

        fun localAxisA(value: Vec): Mut

        fun localAxisB(value: Vec): Mut

        fun localAnchorA(value: Vec): Mut

        fun localAnchorB(value: Vec): Mut

        fun contactsEnabled(value: Boolean): Mut

        fun lockAll(vararg degrees: Dof): Mut

        fun freeAll(vararg degrees: Dof): Mut
    }

    interface Own : Mut {
        override fun localFrameA(value: Iso): Own

        override fun localFrameB(value: Iso): Own

        override fun localAxisA(value: Vec): Own

        override fun localAxisB(value: Vec): Own

        override fun localAnchorA(value: Vec): Own

        override fun localAnchorB(value: Vec): Own

        override fun contactsEnabled(value: Boolean): Own

        override fun lockAll(vararg degrees: Dof): Own

        override fun freeAll(vararg degrees: Dof): Own
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
    interface Mut : MultibodyJoint, Joint.Mut
}
