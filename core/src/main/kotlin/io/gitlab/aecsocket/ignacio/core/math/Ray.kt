package io.gitlab.aecsocket.ignacio.core.math

import io.gitlab.aecsocket.ignacio.core.IgScalar

data class Ray(
    val origin: Vec3,
    val direction: Vec3
) {
    val directionInv = direction.inverse()

    fun at(t: IgScalar) = origin + direction * t

    fun asString(fmt: String = "%f") = "Ray(${origin.asString(fmt)}, ${direction.asString(fmt)})"

    override fun toString() = asString(DECIMAL_FORMAT)

    override fun equals(other: Any?) = other is Ray &&
        origin == other.origin && direction == other.direction
}
