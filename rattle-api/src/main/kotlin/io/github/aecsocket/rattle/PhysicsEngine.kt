package io.github.aecsocket.rattle

import io.github.aecsocket.klam.*
import kotlin.math.max

typealias Real = Double
typealias Vec = DVec3
typealias Quat = DQuat
typealias Mat3 = DMat3
typealias Iso = DIso3
typealias Affine = DAffine3

const val DEFAULT_FRICTION: Real = 0.5
const val DEFAULT_RESTITUTION: Real = 0.0
const val DEFAULT_LINEAR_DAMPING: Real = 0.0
const val DEFAULT_ANGULAR_DAMPING: Real = 0.0

interface Destroyable {
    fun destroy()
}

interface RefCounted {
    val refCount: Long

    fun acquire(): RefCounted

    fun release(): RefCounted
}

interface PhysicsEngine : Destroyable {
    // a human-readable, or branded, name of the engine
    val name: String

    val version: String

    fun createMaterial(
        friction: Real = DEFAULT_FRICTION,
        restitution: Real = DEFAULT_RESTITUTION,
        frictionCombine: CoeffCombineRule = CoeffCombineRule.AVERAGE,
        restitutionCombine: CoeffCombineRule = CoeffCombineRule.AVERAGE,
    ): PhysicsMaterial

    fun createShape(geom: Geometry): Shape

    fun createCollider(
        shape: Shape,
        material: PhysicsMaterial,
        position: Iso = Iso(),
        isSensor: Boolean = false,
    ): Collider

    fun createFixedBody(
        position: Iso,
    ): FixedBody

    fun createMovingBody(
        position: Iso,
        isKinematic: Boolean = false,
        isCcdEnabled: Boolean = false,
        linearVelocity: Vec = Vec.Zero,
        angularVelocity: Vec = Vec.Zero,
        gravityScale: Real = 1.0,
        linearDamping: Real = DEFAULT_LINEAR_DAMPING,
        angularDamping: Real = DEFAULT_ANGULAR_DAMPING,
        sleeping: Sleeping = Sleeping.Enabled(false),
    ): MovingBody

    fun createSpace(settings: PhysicsSpace.Settings): PhysicsSpace
}

fun numThreads(raw: Int) = if (raw > 0) raw else {
    max(Runtime.getRuntime().availableProcessors() - 2, 1)
}
