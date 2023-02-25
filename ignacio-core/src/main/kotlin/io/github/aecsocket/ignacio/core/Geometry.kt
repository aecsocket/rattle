package io.github.aecsocket.ignacio.core

import io.github.aecsocket.ignacio.core.math.Quat
import io.github.aecsocket.ignacio.core.math.Transform
import io.github.aecsocket.ignacio.core.math.Vec3f
import io.github.aecsocket.ignacio.core.math.sp

sealed interface Geometry

data class SphereGeometry(val radius: Float) : Geometry

data class BoxGeometry(val halfExtent: Vec3f) : Geometry

data class CapsuleGeometry(val halfHeight: Float, val radius: Float) : Geometry

data class CompoundChild(val position: Vec3f, val rotation: Quat, val geometry: Geometry) {
    constructor(transform: Transform, geometry: Geometry) : this(transform.position.sp(), transform.rotation, geometry)
}

data class StaticCompoundGeometry(val children: List<CompoundChild>) : Geometry
