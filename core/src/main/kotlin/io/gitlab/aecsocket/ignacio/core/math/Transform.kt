package io.gitlab.aecsocket.ignacio.core.math

data class Transform(
    val position: Vec3,
    val rotation: Quat
) {
    fun asString(fmt: String = "%f") = "[${position.asString(fmt)}, ${rotation.asString(fmt)}]"

    override fun toString() = asString("%.3f")
}
