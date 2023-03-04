package io.github.aecsocket.ignacio.core

import io.github.aecsocket.ignacio.core.math.FPI
import io.github.aecsocket.ignacio.core.math.Transform
import io.github.aecsocket.ignacio.core.math.Vec3f

interface BodySettings {
    val geometry: Geometry
}

data class StaticBodySettings(
    override val geometry: Geometry,
) : BodySettings

data class DynamicBodySettings(
    override val geometry: Geometry,
    val mass: Float = 1.0f,
    val linearVelocity: Vec3f = Vec3f.Zero,
    val angularVelocity: Vec3f = Vec3f.Zero,
    val friction: Float = 0.2f,
    val restitution: Float = 0.0f,
    val linearDamping: Float = 0.05f,
    val angularDamping: Float = 0.05f,
    val maxLinearVelocity: Float = 500.0f,
    val maxAngularVelocity: Float = 0.25f * FPI * 60.0f,
    val gravityFactor: Float = 1.0f,
) : BodySettings

interface BodyAccess {
    var transform: Transform

    fun asStatic(): StaticBodyAccess

    fun asDynamic(): DynamicBodyAccess
}

interface StaticBodyAccess : BodyAccess {

}

interface DynamicBodyAccess : BodyAccess {

}
