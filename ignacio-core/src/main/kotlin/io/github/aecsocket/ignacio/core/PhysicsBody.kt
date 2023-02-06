package io.github.aecsocket.ignacio.core

import io.github.aecsocket.ignacio.core.math.Transform
import org.spongepowered.configurate.objectmapping.ConfigSerializable

@ConfigSerializable
data class BodyDynamics(
    val activate: Boolean = true,
    val mass: Float = 1f,
)

interface PhysicsBody : Destroyable {
    var transform: Transform
}

interface RigidBody : PhysicsBody

interface StaticBody : RigidBody

interface DynamicBody : RigidBody
