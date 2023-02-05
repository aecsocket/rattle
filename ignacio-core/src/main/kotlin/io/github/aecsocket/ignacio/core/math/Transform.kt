package io.github.aecsocket.ignacio.core.math

import org.spongepowered.configurate.objectmapping.ConfigSerializable

@ConfigSerializable
data class Transform(
    val position: Vec3d = Vec3d.Zero,
    val rotation: Quat = Quat.Identity
) {
    companion object {
        val Identity = Transform()
    }

    fun left() = rotation * Vec3d.Left
    fun right() = rotation * Vec3d.Right
    fun up() = rotation * Vec3d.Up
    fun down() = rotation * Vec3d.Down
    fun forward() = rotation * Vec3d.Forward
    fun backward() = rotation * Vec3d.Backward

    fun inverse(): Transform {
        val rotInv = rotation.inverse()
        return Transform(rotInv * -position, rotInv)
    }

    operator fun times(t: Transform) = Transform(
        rotation * t.position + position,
        rotation * t.rotation
    )

    fun apply(v: Vec3d) = rotation * v + position
    fun apply(r: Ray) = Ray(apply(r.origin), rotation * r.direction)

    fun invert(v: Vec3d) = rotation.inverse() * (v - position)
    fun invert(r: Ray) = Ray(invert(r.origin), rotation.inverse() * r.direction)

    fun asString(fmt: String = "%f") = "[${position.asString(fmt)}, ${rotation.asString(fmt)}]"

    override fun toString() = asString(DECIMAL_FORMAT)
}
