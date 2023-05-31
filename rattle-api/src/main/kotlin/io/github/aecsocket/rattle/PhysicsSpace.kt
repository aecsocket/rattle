package io.github.aecsocket.rattle

import org.spongepowered.configurate.objectmapping.ConfigSerializable

/**
 * A linear axis in 3D space.
 */
enum class LinAxis {
    X,
    Y,
    Z
}

interface PhysicsSpace : Destroyable {
    @ConfigSerializable
    data class Settings(
        val gravity: Vec = Vec(0.0, -9.81, 0.0),
    )

    var settings: Settings

    val colliders: SingleContainer<Collider>

    val bodies: ActiveContainer<RigidBody>

    val impulseJoints: JointContainer<ImpulseJoint>

    val multibodyJoints: JointContainer<MultibodyJoint>

    interface Container<T> {
        val count: Int

        fun all(): Collection<T>
    }

    interface SingleContainer<T> : Container<T> {
        fun add(value: T)

        fun remove(value: T)
    }

    interface ActiveContainer<T> : SingleContainer<T> {
        val activeCount: Int

        fun active(): Collection<T>
    }

    interface JointContainer<T> : Container<T> {
        fun add(value: T, bodyA: RigidBody, bodyB: RigidBody)

        fun remove(value: T)
    }
}
