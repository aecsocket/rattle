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

    fun createCollider(
        shape: Shape,
        material: PhysicsMaterial,
        position: Iso = Iso(),
        isSensor: Boolean = false,
    ): Collider

    fun <VR : VolumeAccess, VW : VR> addFixedBody(
        position: Iso,
        volume: Volume<VR, VW>,
    ): FixedBodyHandle<VR, VW>

    fun <VR : VolumeAccess, VW : VR> addMovingBody(
        position: Iso,
        volume: Volume<VR, VW>,
        linearVelocity: Vec = Vec.Zero,
        angularVelocity: Vec = Vec.Zero,
    ): MovingBodyHandle<VR, VW>

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
