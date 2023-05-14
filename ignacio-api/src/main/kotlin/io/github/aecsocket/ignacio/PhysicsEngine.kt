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

interface RefCounted {
    val refCount: Long

    fun acquire(): Any

    fun release(): Any
}

interface PhysicsEngine : Destroyable {
    val version: String

    fun createMaterial(desc: PhysicsMaterialDesc): PhysicsMaterial

    fun createShape(geom: Geometry): Shape

    fun <CR : Collider<CR, CW>, CW : CR> createCollider(desc: ColliderDesc): Collider<CR, CW>

    fun createSpace(settings: PhysicsSpace.Settings): PhysicsSpace
}

fun numThreads(raw: Int) = if (raw > 0) raw else {
    max(Runtime.getRuntime().availableProcessors() - 2, 1)
}
