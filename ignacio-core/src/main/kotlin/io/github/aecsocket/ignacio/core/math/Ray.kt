package io.github.aecsocket.ignacio.core.math

data class Ray(
    val origin: Vec3d,
    val direction: Vec3f
) {
    val directionInv = direction.inverse()

    fun at(t: Float) = origin + (direction * t).d()

    fun asString(fmt: String = "%f") = "Ray(${origin.asString(fmt)}, ${direction.asString(fmt)})"

    override fun toString() = asString(DECIMAL_FORMAT)
}
