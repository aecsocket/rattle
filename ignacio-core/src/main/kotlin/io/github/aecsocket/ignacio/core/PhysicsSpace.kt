package io.github.aecsocket.ignacio.core

import io.github.aecsocket.ignacio.core.math.Transform
import io.github.aecsocket.ignacio.core.math.Vec3f
import org.spongepowered.configurate.objectmapping.ConfigSerializable

interface PhysicsSpace {
    @ConfigSerializable
    data class Settings(
        val gravity: Vec3f = Vec3f(0f, -9.81f, 0f),
        val groundPlaneY: Float = -128f,
    )

    fun addStaticBody(geometry: Geometry, transform: Transform): StaticBody

    fun addDynamicBody(geometry: Geometry, transform: Transform): DynamicBody

    fun removeBody(body: PhysicsBody)

    fun update(deltaTime: Float)
}
