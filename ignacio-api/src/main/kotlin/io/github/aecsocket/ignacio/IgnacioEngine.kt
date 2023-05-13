package io.github.aecsocket.ignacio

import io.github.aecsocket.klam.*
import kotlin.math.max

typealias Real = Double
typealias Vec = DVec3
typealias Quat = DQuat
typealias Mat3 = DMat3
typealias Iso = DIsometry3
typealias Affine = DAffine3

const val DEFAULT_FRICTION: Real = 0.5
const val DEFAULT_RESTITUTION: Real = 0.0
const val DEFAULT_LINEAR_DAMPING: Real = 0.05
const val DEFAULT_ANGULAR_DAMPING: Real = 0.05

interface Destroyable {
    fun destroy()
}

interface RefCounted {
    val refCount: Long

    fun increment()

    fun decrement()
}

interface IgnacioEngine : Destroyable {
    fun createShape(geom: Geometry): Shape

    fun createSpace(settings: PhysicsSpace.Settings): PhysicsSpace
}

fun numThreads(raw: Int) = if (raw > 0) raw else {
    max(Runtime.getRuntime().availableProcessors() - 2, 1)
}
