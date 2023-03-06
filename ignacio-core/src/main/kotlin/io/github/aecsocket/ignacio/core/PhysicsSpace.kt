package io.github.aecsocket.ignacio.core

import io.github.aecsocket.ignacio.core.math.Ray
import io.github.aecsocket.ignacio.core.math.Transform
import io.github.aecsocket.ignacio.core.math.Vec3d
import io.github.aecsocket.ignacio.core.math.Vec3f
import org.spongepowered.configurate.objectmapping.ConfigSerializable

fun interface StepListener {
    fun onStep(deltaTime: Float)
}

interface ContactListener {
    fun onAdded(body1: BodyRef.Read, body2: BodyRef.Read)

    fun onRemoved(body1: BodyRef, body2: BodyRef)
}

interface PhysicsSpace : Destroyable {
    @ConfigSerializable
    data class Settings(
        val gravity: Vec3f = Vec3f(0f, -9.81f, 0f),
    )

    data class RayCast(
        val body: BodyRef,
    )

    var settings: Settings

    interface Bodies {
        val num: Int
        val numActive: Int

        fun createStatic(settings: StaticBodySettings, transform: Transform): BodyRef.StaticWrite

        fun createMoving(settings: MovingBodySettings, transform: Transform): BodyRef.MovingWrite

        fun destroy(bodyRef: BodyRef)

        fun destroyAll(bodyRefs: Collection<BodyRef>)

        fun add(bodyRef: BodyRef, activate: Boolean)

        fun addAll(bodyRefs: Collection<BodyRef>, activate: Boolean)

        fun remove(bodyRef: BodyRef)

        fun removeAll(bodyRefs: Collection<BodyRef>)

        fun addStatic(settings: StaticBodySettings, transform: Transform): BodyRef.StaticWrite

        fun addMoving(settings: MovingBodySettings, transform: Transform, activate: Boolean): BodyRef.MovingWrite

        fun all(): Collection<BodyRef>

        fun active(): Collection<BodyRef>
    }
    val bodies: Bodies

    interface BroadQuery {
        fun overlapSphere(position: Vec3d, radius: Float): Collection<BodyRef>
    }
    val broadQuery: BroadQuery

    interface NarrowQuery {
        fun rayCastBody(ray: Ray, distance: Float): RayCast?

        fun rayCastBodies(ray: Ray, distance: Float): Collection<BodyRef>
    }
    val narrowQuery: NarrowQuery

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
