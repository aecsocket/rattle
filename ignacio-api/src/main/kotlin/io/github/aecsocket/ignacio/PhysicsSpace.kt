package io.github.aecsocket.ignacio

import org.spongepowered.configurate.objectmapping.ConfigSerializable

interface PhysicsSpace : Destroyable {
    @ConfigSerializable
    data class Settings(
        val gravity: Vec = Vec(0.0, -9.81, 0.0),
    )

    var settings: Settings

    fun startStep(dt: Real)

    fun finishStep()

    val bodies: Bodies
    interface Bodies {
        fun <CR : Collider<CR, CW>, CW : CR> createFixed(
            position: Iso,
            collider: CR,
        ): FixedBodyHandle.Write<CR, CW>
    }

//
//    val rigidBodies: RigidBodies
//    interface RigidBodies {
//        fun <C : Colliders> createFixed(
//            position: Iso,
//            collider: ColliderDesc<C>,
//        ): FixedBody.Shaped<C>
//
//        fun <C : Colliders> createMoving(
//            position: Iso,
//            collider: ColliderDesc<C>,
//            isKinematic: Boolean = false,
//            linearVelocity: Vec = Vec.Zero,
//            angularVelocity: Vec = Vec.Zero,
//            gravityScale: Real = 1.0,
//            linearDamping: Real = DEFAULT_LINEAR_DAMPING,
//            angularDamping: Real = DEFAULT_ANGULAR_DAMPING,
//            isCcdEnabled: Boolean = false,
//            canSleep: Boolean = true,
//            isSleeping: Boolean = false,
//        ): MovingBody.Shaped<C>
//    }
}
