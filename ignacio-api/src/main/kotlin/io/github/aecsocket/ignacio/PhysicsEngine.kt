package io.github.aecsocket.ignacio

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
const val DEFAULT_LINEAR_DAMPING: Real = 0.05
const val DEFAULT_ANGULAR_DAMPING: Real = 0.05

interface Destroyable {
    fun destroy()
}

interface PhysicsEngine : Destroyable {
    val version: String

    fun createMaterial(
        friction: Real = DEFAULT_FRICTION,
        restitution: Real = DEFAULT_RESTITUTION,
        frictionCombine: CoeffCombineRule = CoeffCombineRule.AVERAGE,
        restitutionCombine: CoeffCombineRule = CoeffCombineRule.AVERAGE,
    ): PhysicsMaterial

    fun createShape(geom: Geometry): Shape

    fun createSpace(settings: PhysicsSpace.Settings): PhysicsSpace
}

fun numThreads(raw: Int) = if (raw > 0) raw else {
    max(Runtime.getRuntime().availableProcessors() - 2, 1)
}
