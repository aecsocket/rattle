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

/**
 * An independent object storing simulation data for a set of physics structures. This takes ownership of objects like
 * [RigidBody] and [Collider] instances, and allows manipulating and querying the internal structures. An instance
 * can be created through [PhysicsEngine.createSpace].
 */
interface PhysicsSpace : Destroyable {
    /**
     * Simulation parameters for a physics space.
     * @param gravity The gravity **acceleration** applied to all bodies, in meters/sec.
     */
    @ConfigSerializable
    data class Settings(
        val gravity: Vec = Vec(0.0, -9.81, 0.0),
    )

    var settings: Settings

    val colliders: SingleContainer<Collider, Collider.Mut, Collider.Own, ColliderKey>

    val bodies: ActiveContainer<RigidBody, RigidBody.Mut, RigidBody.Own, RigidBodyKey>

//    val impulseJoints: JointContainer<ImpulseJoint, ImpulseJoint.Mut, ImpulseJoint.Own, ImpulseJointKey>
//
//    val multibodyJoints: JointContainer<MultibodyJoint, MultibodyJoint.Mut, MultibodyJoint.Own, MultibodyJointKey>

    fun attach(coll: ColliderKey, to: RigidBodyKey)

    fun detach(coll: ColliderKey)

    interface Container<R, W : R, O : W, K> {
        val count: Int

        fun read(key: K): R?

        fun write(key: K): W?

        fun all(): Collection<K>

        fun remove(key: K): O?
    }

    interface SingleContainer<R, W : R, O : W, K> : Container<R, W, O, K> {
        fun add(value: O): K
    }

    interface ActiveContainer<R, W : R, O : W, K> : SingleContainer<R, W, O, K> {
        val activeCount: Int

        fun active(): Collection<K>
    }

    interface JointContainer<R, W : R, O : W, K> : Container<R, W, O, K> {
        fun add(value: O, bodyA: RigidBodyKey, bodyB: RigidBodyKey)
    }
}
