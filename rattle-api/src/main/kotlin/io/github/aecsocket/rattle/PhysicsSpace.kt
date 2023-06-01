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

    val colliders: SingleContainer<Collider.Read, Collider.Write, Collider.Own, ColliderHandle>

    val bodies: ActiveContainer<RigidBody.Read, RigidBody.Write, RigidBody.Own, RigidBodyHandle>

//    val impulseJoints: JointContainer<ImpulseJoint>
//
//    val multibodyJoints: JointContainer<MultibodyJoint>

    fun attach(coll: ColliderHandle, to: RigidBodyHandle)

    fun detach(coll: ColliderHandle)

    interface Container<R, W : R, O : W, H> {
        val count: Int

        fun read(handle: H): R?

        fun write(handle: H): W?

        fun all(): Collection<H>

        fun remove(handle: H): O?
    }

    interface SingleContainer<R, W : R, O : W, H> : Container<R, W, O, H> {
        fun add(value: O): H
    }

    interface ActiveContainer<R, W : R, O : W, H> : SingleContainer<R, W, O, H> {
        val activeCount: Int

        fun active(): Collection<H>
    }

//    interface JointContainer<R, W : R, > : Container<T> {
//        fun add(value: T, bodyA: RigidBody, bodyB: RigidBody)
//
//        fun remove(value: T)
//    }
}
