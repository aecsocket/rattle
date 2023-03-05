package io.github.aecsocket.ignacio.core

import io.github.aecsocket.ignacio.core.math.Quat
import io.github.aecsocket.ignacio.core.math.Transform
import io.github.aecsocket.ignacio.core.math.Vec3f
import io.github.aecsocket.ignacio.core.math.f

interface Geometry : Destroyable

sealed interface GeometrySettings

data class SphereGeometrySettings(val radius: Float) : GeometrySettings

data class BoxGeometrySettings(val halfExtent: Vec3f) : GeometrySettings

data class CapsuleGeometrySettings(val halfHeight: Float, val radius: Float) : GeometrySettings

data class CompoundChild(val position: Vec3f, val rotation: Quat, val geometry: Geometry) {
    constructor(transform: Transform, geometry: Geometry) : this(transform.position.f(), transform.rotation, geometry)
}

data class StaticCompoundGeometrySettings(val children: List<CompoundChild>) : GeometrySettings
