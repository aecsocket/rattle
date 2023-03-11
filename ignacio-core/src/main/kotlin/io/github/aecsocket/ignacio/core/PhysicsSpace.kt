package io.github.aecsocket.ignacio.core

import io.github.aecsocket.ignacio.core.math.*
import org.spongepowered.configurate.objectmapping.ConfigSerializable

fun interface StepListener {
    fun onStep(deltaTime: Float)
}

interface ContactListener {
    fun onAdded(body1: PhysicsBody.Read, body2: PhysicsBody.Read, manifold: ContactManifold) {}

    fun onRemoved(body1: PhysicsBody, body2: PhysicsBody) {}
}

interface PhysicsSpace : Destroyable {
    @ConfigSerializable
    data class Settings(
        val gravity: Vec3f = Vec3f(0f, -9.81f, 0f),
    )

    data class RayCast(
        val body: PhysicsBody,
    )

    var settings: Settings

    val bodies: Bodies
    interface Bodies {
        val num: Int
        val numActive: Int

        fun createStatic(settings: StaticBodySettings, transform: Transform): PhysicsBody.StaticWrite

        fun createMoving(settings: MovingBodySettings, transform: Transform): PhysicsBody.MovingWrite

        fun create(settings: BodySettings, transform: Transform): PhysicsBody.Write

        fun destroy(body: PhysicsBody)

        fun destroyAll(bodies: Collection<PhysicsBody>)

        fun add(body: PhysicsBody, activate: Boolean)

        fun addAll(bodies: Collection<PhysicsBody>, activate: Boolean)

        fun remove(body: PhysicsBody)

        fun removeAll(bodies: Collection<PhysicsBody>)

        fun addStatic(settings: StaticBodySettings, transform: Transform): PhysicsBody.StaticWrite

        fun addMoving(settings: MovingBodySettings, transform: Transform, activate: Boolean): PhysicsBody.MovingWrite

        fun all(): Collection<PhysicsBody>

        fun active(): Collection<PhysicsBody>
    }

    val broadQuery: BroadQuery
    interface BroadQuery {
        fun overlapSphere(position: Vec3d, radius: Float, filter: BroadFilter? = null): Collection<PhysicsBody>

        fun overlapAABox(box: AABB, filter: BroadFilter? = null): Collection<PhysicsBody>
    }

    val narrowQuery: NarrowQuery
    interface NarrowQuery {
        fun rayCastBody(ray: Ray, distance: Float): RayCast?

        fun rayCastBodies(ray: Ray, distance: Float): Collection<PhysicsBody>
    }

    fun onStep(listener: StepListener)

    fun removeStepListener(listener: StepListener)

    fun onContact(listener: ContactListener)

    fun removeContactListener(listener: ContactListener)

    fun update(deltaTime: Float)
}

fun <R> PhysicsSpace.bodies(block: PhysicsSpace.Bodies.() -> R): R {
    return block(bodies)
}

fun <R> PhysicsSpace.broadQuery(block: PhysicsSpace.BroadQuery.() -> R): R {
    return block(broadQuery)
}

fun <R> PhysicsSpace.narrowQuery(block: PhysicsSpace.NarrowQuery.() -> R): R {
    return block(narrowQuery)
}
