package io.github.aecsocket.rattle

import org.spongepowered.configurate.objectmapping.ConfigSerializable

interface PhysicsSpace : Destroyable {
    @ConfigSerializable
    data class Settings(
        val gravity: Vec = Vec(0.0, -9.81, 0.0),
    )

    var settings: Settings

    val colliders: Container<Collider>

    val bodies: ActiveContainer<RigidBody>

    interface Container<T> {
        val count: Int

        fun add(value: T)

        fun remove(value: T)
    }

    interface ActiveContainer<T> : Container<T> {
        val activeCount: Int
    }
}
