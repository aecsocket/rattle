package io.github.aecsocket.ignacio.core

import io.github.aecsocket.ignacio.core.math.Quat
import io.github.aecsocket.ignacio.core.math.Transform
import io.github.aecsocket.ignacio.core.math.Vec3f
import io.github.aecsocket.ignacio.core.math.f

sealed interface Geometry

data class SphereGeometry(val radius: Float) : Geometry

data class BoxGeometry(val halfExtent: Vec3f) : Geometry

data class CapsuleGeometry(val halfHeight: Float, val radius: Float) : Geometry

data class CompoundChild(val shape: Shape, val position: Vec3f, val rotation: Quat) {
    constructor(shape: Shape, transform: Transform) : this(shape, transform.position.f(), transform.rotation)
}

data class StaticCompoundGeometry(val children: Collection<CompoundChild>) : Geometry
