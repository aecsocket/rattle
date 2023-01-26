package io.gitlab.aecsocket.ignacio.core.math

import org.spongepowered.configurate.objectmapping.ConfigSerializable

@ConfigSerializable
data class Transform(
    val position: Vec3 = Vec3.Zero,
    val rotation: Quat = Quat.Identity
) {
    companion object {
        val Identity = Transform()

        fun delta(from: Transform, to: Transform) = from.inverse() * to
    }

    fun left() = rotation * Vec3.Left
    fun right() = rotation * Vec3.Right
    fun up() = rotation * Vec3.Up
    fun down() = rotation * Vec3.Down
    fun forward() = rotation * Vec3.Forward
    fun backward() = rotation * Vec3.Backward

    fun inverse(): Transform {
        val rotationInv = rotation.inverse()
        return Transform(rotationInv * -position, rotationInv)
    }

    operator fun times(t: Transform) = Transform(
        rotation * t.position + position,
        rotation * t.rotation
    )

    fun apply(v: Vec3) = rotation * v + position
    fun apply(r: Ray) = Ray(apply(r.origin), rotation * r.direction)

    fun invert(v: Vec3) = rotation.inverse() * (v - position)
    fun invert(r: Ray) = Ray(invert(r.origin), rotation.inverse() * r.direction)

    fun asString(fmt: String = "%f") = "[${position.asString(fmt)}, ${rotation.asString(fmt)}]"

    override fun toString() = asString("%.3f")
}
