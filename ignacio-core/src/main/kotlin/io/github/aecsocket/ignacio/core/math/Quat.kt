package io.github.aecsocket.ignacio.core.math

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Quat(val x: Float, val y: Float, val z: Float, val w: Float) {
    companion object {
        val Identity = Quat(0f, 0f, 0f, 1f)
    }

    fun norm() = x*x + y*y + z*z + w*w
    fun length() = sqrt(norm())
    fun inverse() = Quat(-x, -y, -z, w)
    fun normalized(): Quat {
        val length = length()
        return Quat(x/length, y/length, z/length, w/length)
    }

    operator fun times(q: Quat) = Quat(
        x*q.w + y*q.z - z*q.y + w*q.x,
        -x*q.z + y*q.w + z*q.x + w*q.y,
        x*q.y - y*q.x + z*q.w + w*q.z,
        -x*q.x - y*q.y - z*q.z + w*q.w
    )
    operator fun times(s: Float) = Quat(x*s, y*s, z*s, w*s)
    operator fun times(v: Vec3f): Vec3f {
        val u = Vec3f(x, y, z)
        return (u * 2f * u.dot(v)) +
                (v * (w*w - u.dot(u))) +
                (u.cross(v) * 2f * w)
    }
    operator fun times(v: Vec3d): Vec3d {
        val u = Vec3d(x.toDouble(), y.toDouble(), z.toDouble())
        return (u * 2.0 * u.dot(v)) +
                (v * (w*w - u.dot(u))) +
                (u.cross(v) * 2.0 * w.toDouble())
    }

    fun asString(fmt: String = "%f") = "($fmt + ${fmt}i + ${fmt}j + ${fmt}k)".format(w, x, y, z)

    override fun toString() = asString(DECIMAL_FORMAT)

    override fun equals(other: Any?) = other is Quat &&
            x.compareTo(other.x) == 0 && y.compareTo(other.y) == 0 && z.compareTo(other.z) == 0 && w.compareTo(other.w) == 0

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + z.hashCode()
        result = 31 * result + w.hashCode()
        return result
    }
}
