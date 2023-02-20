package io.github.aecsocket.ignacio.core

import io.github.aecsocket.ignacio.core.math.Vec3f

sealed interface Geometry

data class SphereGeometry(val radius: Float) : Geometry

data class BoxGeometry(val halfExtent: Vec3f) : Geometry

data class CapsuleGeometry(val halfHeight: Float, val radius: Float) : Geometry
