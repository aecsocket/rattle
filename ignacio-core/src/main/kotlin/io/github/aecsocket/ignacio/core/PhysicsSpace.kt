package io.github.aecsocket.ignacio.core

import io.github.aecsocket.ignacio.core.math.Ray
import io.github.aecsocket.ignacio.core.math.Transform
import io.github.aecsocket.ignacio.core.math.Vec3d
import io.github.aecsocket.ignacio.core.math.Vec3f
import org.spongepowered.configurate.objectmapping.ConfigSerializable

interface PhysicsSpace {
    @ConfigSerializable
    data class Settings(
        val gravity: Vec3f = Vec3f(0f, -9.81f, 0f),
    )

    var settings: Settings

    val numBodies: Int
    val numActiveBodies: Int

    val bodies: Bodies
    val broadQuery: BroadQuery
    val narrowQuery: NarrowQuery

    fun update(deltaTime: Float)

    data class RayCast(
        val body: BodyAccess,
    )

    interface Bodies {
        fun createStatic(settings: StaticBodySettings, transform: Transform): StaticBodyAccess

        fun createDynamic(settings: DynamicBodySettings, transform: Transform): DynamicBodyAccess

        fun destroy(body: BodyAccess)

        fun add(body: BodyAccess, activate: Boolean)

        fun addStatic(settings: StaticBodySettings, transform: Transform): StaticBodyAccess

        fun addDynamic(settings: DynamicBodySettings, transform: Transform, activate: Boolean): DynamicBodyAccess

        fun remove(body: BodyAccess)
    }

    interface BroadQuery {
        fun overlapSphere(position: Vec3d, radius: Float): Collection<BodyAccess>
    }

    interface NarrowQuery {
        fun rayCastBody(ray: Ray, distance: Float): RayCast?

        fun rayCastBodies(ray: Ray, distance: Float): Collection<BodyAccess>
    }
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
