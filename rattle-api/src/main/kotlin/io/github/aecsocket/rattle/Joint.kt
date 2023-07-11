package io.github.aecsocket.rattle

import io.github.aecsocket.klam.*

/**
 * A key used to index into a [PhysicsSpace] to gain a reference, mutable or immutable, to an
 * [ImpulseJoint].
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
        val min: Double,
        val max: Double,
        val impulse: Double,
    ) : State

    /* TODO: Kotlin 1.9 data */ object Locked : State
  }

  sealed interface Motor {
    /* TODO: Kotlin 1.9 data */ object Disabled : Motor

    data class Enabled(
        val targetVel: Double,
        val targetPos: Double,
        val stiffness: Double,
        val damping: Double,
        val maxForce: Double,
        val impulse: Double,
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
  val localFrameA: DIso3

  val localFrameB: DIso3

  // these four fields are just accessors into a slice of the localFrame effectively
  // but they're more convenient to use
  val localAxisA: DVec3

  val localAxisB: DVec3

  val localAnchorA: DVec3

  val localAnchorB: DVec3

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

    fun localFrameA(value: DIso3): Mut

    fun localFrameB(value: DIso3): Mut

    fun localAxisA(value: DVec3): Mut

    fun localAxisB(value: DVec3): Mut

    fun localAnchorA(value: DVec3): Mut

    fun localAnchorB(value: DVec3): Mut

    fun contactsEnabled(value: Boolean): Mut

    fun lockAll(vararg degrees: Dof): Mut

    fun freeAll(vararg degrees: Dof): Mut
  }

  interface Own : Mut {
    override fun localFrameA(value: DIso3): Own

    override fun localFrameB(value: DIso3): Own

    override fun localAxisA(value: DVec3): Own

    override fun localAxisB(value: DVec3): Own

    override fun localAnchorA(value: DVec3): Own

    override fun localAnchorB(value: DVec3): Own

    override fun contactsEnabled(value: Boolean): Own

    override fun lockAll(vararg degrees: Dof): Own

    override fun freeAll(vararg degrees: Dof): Own
  }
}

interface ImpulseJoint : Joint {
  val bodyA: RigidBodyKey

  val bodyB: RigidBodyKey

  val translationImpulses: DVec3

  val rotationImpulses: DVec3

  interface Mut : ImpulseJoint, Joint.Mut {
    fun bodyA(value: RigidBodyKey): Mut

    fun bodyB(value: RigidBodyKey): Mut
  }
}

interface MultibodyJoint : Joint {
  interface Mut : MultibodyJoint, Joint.Mut
}
