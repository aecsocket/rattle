package io.gitlab.aecsocket.ignacio.core.math

import io.gitlab.aecsocket.ignacio.core.IgScalar
import io.gitlab.aecsocket.ignacio.core.igForce
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.serialize.SerializationException
import org.spongepowered.configurate.serialize.TypeSerializer
import java.lang.reflect.Type
import kotlin.math.acos
import kotlin.math.sqrt

data class Vec3(val x: IgScalar, val y: IgScalar, val z: IgScalar) {
    companion object {
        val Zero = Vec3(0.0)
        val One = Vec3(1.0)
        val X = Vec3(1.0, 0.0, 0.0)
        val Y = Vec3(0.0, 1.0, 0.0)
        val Z = Vec3(0.0, 0.0, 1.0)

        val Left = Vec3(1.0, 0.0, 0.0)
        val Right = Vec3(-1.0, 0.0, 0.0)
        val Up = Vec3(0.0, 1.0, 0.0)
        val Down = Vec3(0.0, -1.0, 0.0)
        val Forward = Vec3(0.0, 0.0, 1.0)
        val Backward = Vec3(0.0, 0.0, -1.0)
    }

    constructor(n: IgScalar) : this(n, n, n)

    operator fun plus(v: Vec3) = Vec3(x+v.x, y+v.y, z+v.z)
    operator fun plus(s: IgScalar) = Vec3(x+s, y+s, z+s)

    operator fun minus(v: Vec3) = Vec3(x-v.x, y-v.y, z-v.z)
    operator fun minus(s: IgScalar) = Vec3(x-s, y-s, z-s)

    operator fun times(v: Vec3) = Vec3(x*v.x, y*v.y, z*v.z)
    operator fun times(s: IgScalar) = Vec3(x*s, y*s, z*s)

    operator fun div(v: Vec3) = Vec3(x/v.x, y/v.y, z/v.z)
    operator fun div(s: IgScalar) = Vec3(x/s, y/s, z/s)

    operator fun unaryMinus() = Vec3(-x, -y, -z)

    fun abs() = Vec3(kotlin.math.abs(x), kotlin.math.abs(y), kotlin.math.abs(z))
    fun inverse() = Vec3(1/x, 1/y, 1/z)
    fun lengthSqr() = x*x + y*y + z*z
    fun length() = sqrt(lengthSqr())
    fun normalized(): Vec3 {
        val length = length()
        return Vec3(x/length, y/length, z/length)
    }
    fun sign() = Vec3(kotlin.math.sign(x), kotlin.math.sign(y), kotlin.math.sign(z))
    fun min() = kotlin.math.min(x, kotlin.math.min(y, z))
    fun max() = kotlin.math.max(x, kotlin.math.max(y, z))

    fun dot(v: Vec3) = x*v.x + y*v.y + z*v.z
    fun distanceSqr(v: Vec3) = sqr(v.x-x) + sqr(v.y-y) + sqr(v.z-z)
    fun distance(v: Vec3) = sqrt(distanceSqr(v))
    fun midpoint(v: Vec3) = Vec3((x+v.x) / 2, (y+v.y) / 2, (z+v.z) / 2)
    fun angle(v: Vec3) = acos(clamp(dot(v) / (length() * v.length()), -1.0, 1.0))
    fun cross(v: Vec3) = Vec3(
        y*v.z - z*v.y,
        z*v.x - x*v.z,
        x*v.y - y*v.x
    )

    fun asString(fmt: String = "%f") = "($fmt, $fmt, $fmt)".format(x, y, z)

    override fun toString() = asString(DECIMAL_FORMAT)

    override fun equals(other: Any?) = other is Vec3 &&
        x.compareTo(other.x) == 0 && y.compareTo(other.y) == 0 && z.compareTo(other.z) == 0

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + z.hashCode()
        return result
    }
}

object Vec3Serializer : TypeSerializer<Vec3> {
    override fun serialize(type: Type, obj: Vec3?, node: ConfigurationNode) {
        if (obj == null) node.set(null)
        else {
            node.appendListNode().set(obj.x)
            node.appendListNode().set(obj.y)
            node.appendListNode().set(obj.z)
        }
    }

    override fun deserialize(type: Type, node: ConfigurationNode): Vec3 {
        if (node.isList) {
            val list = node.childrenList()
            if (list.size < 3)
                throw SerializationException(node, type, "Vector must be expressed as [x, y, z]")
            return Vec3(
                list[0].igForce(),
                list[1].igForce(),
                list[2].igForce()
            )
        } else {
            val n = node.igForce<IgScalar>()
            return Vec3(n)
        }
    }
}
