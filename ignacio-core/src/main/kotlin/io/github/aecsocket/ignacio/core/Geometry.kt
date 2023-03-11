package io.github.aecsocket.ignacio.core

import io.github.aecsocket.ignacio.core.math.Quat
import io.github.aecsocket.ignacio.core.math.Transform
import io.github.aecsocket.ignacio.core.math.Vec3f
import io.github.aecsocket.ignacio.core.math.f

sealed interface Geometry

const val defaultDensity = 1000.0f

sealed interface ConvexGeometry : Geometry {
    val density: Float
}

data class SphereGeometry(
    val radius: Float,
    override val density: Float = defaultDensity,
) : ConvexGeometry

data class BoxGeometry(
    val halfExtent: Vec3f,
    override val density: Float = defaultDensity,
) : ConvexGeometry

data class CapsuleGeometry(
    val halfHeight: Float,
    val radius: Float,
    override val density: Float = defaultDensity,
) : ConvexGeometry

data class CompoundChild(val shape: Shape, val position: Vec3f, val rotation: Quat) {
    constructor(shape: Shape, transform: Transform) : this(shape, transform.position.f(), transform.rotation)
}

data class StaticCompoundGeometry(val children: Collection<CompoundChild>) : Geometry
