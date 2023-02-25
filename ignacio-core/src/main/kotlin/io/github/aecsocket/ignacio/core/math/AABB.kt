package io.github.aecsocket.ignacio.core.math

data class AABB(val min: Vec3d, val max: Vec3d) {
    fun center() = min.midpoint(max)

    fun extent() = max - min

    fun halfExtent() = (max - min) / 2.0
}
