package io.github.aecsocket.ignacio.core.math

import kotlin.math.acos
import kotlin.math.sqrt

data class Vec3f(val x: Float, val y: Float, val z: Float) {
    companion object {
        val Zero = Vec3f(0f)
        val One = Vec3f(0f)
        val X = Vec3f(1f, 0f, 0f)
        val Y = Vec3f(0f, 1f, 0f)
        val Z = Vec3f(0f, 0f, 1f)

        val Left = Vec3f(1f, 0f, 0f)
        val Right = Vec3f(-1f, 0f, 0f)
        val Up = Vec3f(0f, 1f, 0f)
        val Down = Vec3f(0f, -1f, 0f)
        val Forward = Vec3f(0f, 0f, 1f)
        val Backward = Vec3f(0f, 0f, -1f)
    }

    constructor(s: Float) : this(s, s, s)

    operator fun plus(v: Vec3f) = Vec3f(x+v.x, y+v.y, z+v.z)
    operator fun plus(s: Float) = Vec3f(x+s, y+s, z+s)

    operator fun minus(v: Vec3f) = Vec3f(x-v.x, y-v.y, z-v.z)
    operator fun minus(s: Float) = Vec3f(x-s, y-s, z-s)

    operator fun times(v: Vec3f) = Vec3f(x*v.x, y*v.y, z*v.z)
    operator fun times(s: Float) = Vec3f(x*s, y*s, z*s)

    operator fun div(v: Vec3f) = Vec3f(x/v.x, y/v.y, z/v.z)
    operator fun div(s: Float) = Vec3f(x/s, y/s, z/s)

    operator fun unaryMinus() = Vec3f(-x, -y, -z)

    fun abs() = Vec3f(kotlin.math.abs(x), kotlin.math.abs(y), kotlin.math.abs(z))
    fun inverse() = Vec3f(1/x, 1/y, 1/z)
    fun lengthSqr() = x*x + y*y + z*z
    fun length() = sqrt(lengthSqr())
    fun normalized(): Vec3f {
        val length = length()
        return Vec3f(x/length, y/length, z/length)
    }
    fun sign() = Vec3f(kotlin.math.sign(x), kotlin.math.sign(y), kotlin.math.sign(z))
    fun min() = kotlin.math.min(x, kotlin.math.min(y, z))
    fun max() = kotlin.math.max(x, kotlin.math.max(y, z))

    fun dot(v: Vec3f) = x*v.x + y*v.y + z*v.z
    fun distanceSqr(v: Vec3f) = sqr(v.x-x) + sqr(v.y-y) + sqr(v.z-z)
    fun distance(v: Vec3f) = sqrt(distanceSqr(v))
    fun midpoint(v: Vec3f) = Vec3f((x+v.x) / 2, (y+v.y) / 2, (z+v.z) / 2)
    fun angle(v: Vec3f) = acos(clamp(dot(v) / (length() * v.length()), -1f, 1f))
    fun cross(v: Vec3f) = Vec3f(
        y*v.z - z*v.y,
        z*v.x - x*v.z,
        x*v.y - y*v.x
    )

    fun radians() = Vec3f(radians(x), radians(y), radians(z))
    fun degrees() = Vec3f(degrees(x), degrees(y), degrees(z))

    fun asString(fmt: String = "%f") = "($fmt, $fmt, $fmt)".format(x, y, z)

    override fun toString() = asString(DECIMAL_FORMAT)

    override fun equals(other: Any?) = other is Vec3f &&
            x.compareTo(other.x) == 0 && y.compareTo(other.y) == 0 && z.compareTo(other.z) == 0

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + z.hashCode()
        return result
    }
}

data class Vec3d(val x: Double, val y: Double, val z: Double) {
    companion object {
        val Zero = Vec3d(0.0)
        val One = Vec3d(0.0)
        val X = Vec3d(1.0, 0.0, 0.0)
        val Y = Vec3d(0.0, 1.0, 0.0)
        val Z = Vec3d(0.0, 0.0, 1.0)

        val Left = Vec3d(1.0, 0.0, 0.0)
        val Right = Vec3d(-1.0, 0.0, 0.0)
        val Up = Vec3d(0.0, 1.0, 0.0)
        val Down = Vec3d(0.0, -1.0, 0.0)
        val Forward = Vec3d(0.0, 0.0, 1.0)
        val Backward = Vec3d(0.0, 0.0, -1.0)
    }

    constructor(s: Double) : this(s, s, s)

    operator fun plus(v: Vec3d) = Vec3d(x+v.x, y+v.y, z+v.z)
    operator fun plus(s: Double) = Vec3d(x+s, y+s, z+s)

    operator fun minus(v: Vec3d) = Vec3d(x-v.x, y-v.y, z-v.z)
    operator fun minus(s: Double) = Vec3d(x-s, y-s, z-s)

    operator fun times(v: Vec3d) = Vec3d(x*v.x, y*v.y, z*v.z)
    operator fun times(s: Double) = Vec3d(x*s, y*s, z*s)

    operator fun div(v: Vec3d) = Vec3d(x/v.x, y/v.y, z/v.z)
    operator fun div(s: Double) = Vec3d(x/s, y/s, z/s)

    operator fun unaryMinus() = Vec3d(-x, -y, -z)

    fun abs() = Vec3d(kotlin.math.abs(x), kotlin.math.abs(y), kotlin.math.abs(z))
    fun inverse() = Vec3d(1/x, 1/y, 1/z)
    fun lengthSqr() = x*x + y*y + z*z
    fun length() = sqrt(lengthSqr())
    fun normalized(): Vec3d {
        val length = length()
        return Vec3d(x/length, y/length, z/length)
    }
    fun sign() = Vec3d(kotlin.math.sign(x), kotlin.math.sign(y), kotlin.math.sign(z))
    fun min() = kotlin.math.min(x, kotlin.math.min(y, z))
    fun max() = kotlin.math.max(x, kotlin.math.max(y, z))

    fun dot(v: Vec3d) = x*v.x + y*v.y + z*v.z
    fun distanceSqr(v: Vec3d) = sqr(v.x-x) + sqr(v.y-y) + sqr(v.z-z)
    fun distance(v: Vec3d) = sqrt(distanceSqr(v))
    fun midpoint(v: Vec3d) = Vec3d((x+v.x) / 2, (y+v.y) / 2, (z+v.z) / 2)
    fun angle(v: Vec3d) = acos(clamp(dot(v) / (length() * v.length()), -1.0, 1.0))
    fun cross(v: Vec3d) = Vec3d(
        y*v.z - z*v.y,
        z*v.x - x*v.z,
        x*v.y - y*v.x
    )

    fun radians() = Vec3d(radians(x), radians(y), radians(z))
    fun degrees() = Vec3d(degrees(x), degrees(y), degrees(z))

    fun asString(fmt: String = "%f") = "($fmt, $fmt, $fmt)".format(x, y, z)

    override fun toString() = asString(DECIMAL_FORMAT)

    override fun equals(other: Any?) = other is Vec3d &&
            x.compareTo(other.x) == 0 && y.compareTo(other.y) == 0 && z.compareTo(other.z) == 0

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + z.hashCode()
        return result
    }
}
