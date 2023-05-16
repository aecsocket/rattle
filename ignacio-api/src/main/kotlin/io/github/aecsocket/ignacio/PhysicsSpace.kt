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

    fun addCollider(
        shape: Shape,
        material: PhysicsMaterial,
        position: Iso = Iso(),
        isSensor: Boolean = false,
    ): Collider

    fun <VR : VolumeAccess, VW : VR> addFixedBody(
        position: Iso,
        volume: Volume<VR, VW>,
    ): FixedBody<out FixedBody.Read<VR>, out FixedBody.Write<VR, VW>>

    fun <VR : VolumeAccess, VW : VR> addMovingBody(
        position: Iso,
        volume: Volume<VR, VW>,
        isKinematic: Boolean = false,
        linearVelocity: Vec = Vec.Zero,
        angularVelocity: Vec = Vec.Zero,
        gravity: Gravity = Gravity.Enabled,
        linearDamping: Real = DEFAULT_LINEAR_DAMPING,
        angularDamping: Real = DEFAULT_ANGULAR_DAMPING,
        isCcdEnabled: Boolean = false,
        canSleep: Boolean = true,
        isSleeping: Boolean = false,
    ): MovingBody<out MovingBody.Read<VR>, out MovingBody.Write<VR, VW>>
}
