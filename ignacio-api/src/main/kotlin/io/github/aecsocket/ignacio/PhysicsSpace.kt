package io.github.aecsocket.ignacio

import org.spongepowered.configurate.objectmapping.ConfigSerializable

interface PhysicsSpace : Destroyable {
    @ConfigSerializable
    data class Settings(
        val gravity: Vec = Vec(0.0, -9.81, 0.0),
    )

    var settings: Settings

    fun step(dt: Real)

    val colliders: Colliders
    interface Colliders {
        fun create(desc: ColliderDesc): Collider
    }

    val rigidBodies: RigidBodies
    interface RigidBodies {
        fun createFixed(desc: FixedBodyDesc): FixedBody

        fun createMoving(desc: MovingBodyDesc): MovingBody
    }
}
