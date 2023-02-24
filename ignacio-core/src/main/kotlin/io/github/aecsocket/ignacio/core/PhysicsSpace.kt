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

    data class RayCast(
        val body: PhysicsBody,
    )

    val numBodies: Int
    val numActiveBodies: Int

    fun addStaticBody(geometry: Geometry, transform: Transform): StaticBody

    fun addDynamicBody(geometry: Geometry, transform: Transform, dynamics: BodyDynamics): DynamicBody

    fun removeBody(body: PhysicsBody)

    fun rayCastBody(ray: Ray, distance: Float): RayCast?

    fun rayCastBodies(ray: Ray, distance: Float): Collection<PhysicsBody>

    fun bodiesNear(position: Vec3d, radius: Float): Collection<PhysicsBody>

    fun update(deltaTime: Float)
}
