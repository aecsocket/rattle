package io.github.aecsocket.ignacio

sealed interface RigidBodyDesc {
    val position: Iso
}

data class FixedBodyDesc(
    override val position: Iso,
) : RigidBodyDesc

data class MovingBodyDesc(
    override val position: Iso,
    val isKinematic: Boolean = false,
    val linearVelocity: Vec = Vec.Zero,
    val angularVelocity: Vec = Vec.Zero,
    val gravityScale: Real = 1.0,
    val linearDamping: Real = DEFAULT_LINEAR_DAMPING,
    val angularDamping: Real = DEFAULT_ANGULAR_DAMPING,
    val isCcdEnabled: Boolean = false,
    val canSleep: Boolean = true,
    val isSleeping: Boolean = false,
) : RigidBodyDesc


sealed interface RigidBody : Destroyable {
    var position: Iso
}

interface FixedBody : RigidBody

interface MovingBody : RigidBody
