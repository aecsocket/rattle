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

    /**
     * Settings for the simulation of the physics space. Note that not all options will be configurable here, as
     * some are considered implementation details. In this case, the settings need to be configured with the actual
     * implementation.
     */
    var settings: Settings

    /**
     * Provides access to the [Collider] instances in this space.
     */
    val colliders: SimpleContainer<Collider, Collider.Mut, Collider.Own, ColliderKey>

    /**
     * Provides access to the [RigidBody] instances in this space.
     */
    val rigidBodies: ActiveContainer<RigidBody, RigidBody.Mut, RigidBody.Own, RigidBodyKey>

    /**
     * Provides access to the [ImpulseJoint] instances in this space.
     */
    val impulseJoints: ImpulseJointContainer

    /**
     * Provides access to the [MultibodyJoint] instances in this space.
     */
    val multibodyJoints: MultibodyJointContainer

    /**
     * Attaches a [Collider] to a [RigidBody] (see [Collider]).
     */
    fun attach(coll: ColliderKey, to: RigidBodyKey)

    /**
     * Detaches a [Collider] from any parent [RigidBody] (see [Collider]).
     */
    fun detach(coll: ColliderKey)

    interface SimpleContainer<R, W, O, K> {
        val count: Int

        fun all(): Collection<K>

        fun read(key: K): R?

        fun write(key: K): W?

        fun add(value: O): K

        fun remove(key: K): O?
    }

    interface ActiveContainer<R, W, O, K> : SimpleContainer<R, W, O, K> {
        val activeCount: Int

        fun active(): Collection<K>
    }

    interface ImpulseJointContainer {
        val count: Int

        fun all(): Collection<ImpulseJointKey>

        fun read(key: ImpulseJointKey): ImpulseJoint?

        fun write(key: ImpulseJointKey): ImpulseJoint.Mut?

        fun add(value: Joint.Own, bodyA: RigidBodyKey, bodyB: RigidBodyKey): ImpulseJointKey

        fun remove(key: ImpulseJointKey): Joint.Own?
    }

    interface MultibodyJointContainer {
        fun add(value: Joint.Own, bodyA: RigidBodyKey, bodyB: RigidBodyKey)

        fun removeOn(bodyKey: RigidBodyKey)
    }
}
