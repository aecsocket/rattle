package io.gitlab.aecsocket.ignacio.core.math

data class AABB(
    val min: Vec3,
    val max: Vec3
) {
    fun inflated(halfExtent: Vec3) = AABB(min - halfExtent, max + halfExtent)
}
