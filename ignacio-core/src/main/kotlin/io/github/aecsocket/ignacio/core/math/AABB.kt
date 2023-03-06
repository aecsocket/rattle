package io.github.aecsocket.ignacio.core.math

data class AABB(@JvmField val min: Vec3d, @JvmField val max: Vec3d) {
    fun center() = min.midpoint(max)

    fun extent() = max - min

    fun halfExtent() = (max - min) / 2.0
}
