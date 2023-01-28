package io.gitlab.aecsocket.ignacio.core.math

import io.gitlab.aecsocket.ignacio.core.IgScalar
import io.gitlab.aecsocket.ignacio.core.util.force
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.serialize.SerializationException
import org.spongepowered.configurate.serialize.TypeSerializer
import java.lang.reflect.Type
import kotlin.math.*

data class Quat(val x: IgScalar, val y: IgScalar, val z: IgScalar, val w: IgScalar) {
    companion object {
        val Identity = Quat(0.0, 0.0, 0.0, 1.0)

        fun slerp(q1: Quat, q2: Quat, t: IgScalar): Quat {
            var (q2x, q2y, q2z, q2w) = q2
            var result = q1.x*q2x + q1.y*q2y + q1.z*q2z + q1.w*q2w
            if (result < 0.0) {
                q2x *= -1
                q2y *= -1
                q2z *= -1
                q2w *= -1
                result *= -1
            }

            var scale0 = 1 - t
            var scale1 = t
            if ((1 - result) > 0.1) {
                val theta = acos(result)
                val invSinTheta = 1.0 / sin(theta)

                scale0 = sin((1 - t) * theta) * invSinTheta
                scale1 = sin(t * theta) * invSinTheta
            }

            return Quat(
                scale0*q1.x + scale1*q2x,
                scale0*q1.y + scale1*q2y,
                scale0*q1.z + scale1*q2z,
                scale0*q1.w + scale1*q2w,
            )
        }

        fun ofAxisAngle(axis: Vec3, angle: IgScalar): Quat {
            val halfAngle = angle / 2
            val s = sin(halfAngle)
            return Quat(
                axis.x * s,
                axis.y * s,
                axis.z * s,
                cos(halfAngle)
            )
        }

        fun ofAxes(x: Vec3, y: Vec3, z: Vec3): Quat {
            return Mat3(
                x.x, y.x, z.x,
                x.y, y.y, z.y,
                x.z, y.z, z.z
            ).quat()
        }

        fun fromTo(from: Vec3, to: Vec3): Quat {
            var r = from.dot(to) + 1
            val quat = if (r < EPSILON) {
                // `from` and `to` point in opposite directions
                r = 0.0
                if (abs(from.x) > abs(from.z)) Quat(
                    -from.y,
                    from.x,
                    0.0,
                    r,
                ) else Quat(
                    0.0,
                    -from.z,
                    from.y,
                    r
                )
            } else Quat(
                from.y*to.z - from.z*to.y,
                from.z*to.x - from.x*to.z,
                from.x*to.y - from.y*to.x,
                r
            )
            return quat.normalized()
        }

        // same semantics as https://docs.unity3d.com/ScriptReference/Quaternion.LookRotation.html
        fun looking(dir: Vec3, up: Vec3): Quat {
            val v1 = up.cross(dir).normalized()

            return if (v1.lengthSqr() < EPSILON) {
                // `dir` and `up` are collinear
                fromTo(Vec3.Forward, dir)
            } else {
                val v2 = dir.cross(v1).normalized()
                ofAxes(v1, v2, dir)
            }
        }
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
    operator fun times(s: IgScalar) = Quat(x*s, y*s, z*s, w*s)
    operator fun times(v: Vec3): Vec3 {
        val u = Vec3(x, y, z)
        return (u * 2.0 * u.dot(v)) +
            (v * (w*w - u.dot(u))) +
            (u.cross(v) * 2.0 * w)
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

object QuatSerializer : TypeSerializer<Quat> {
    override fun serialize(type: Type, obj: Quat?, node: ConfigurationNode) {
        if (obj == null) node.set(null)
        else {
            node.appendListNode().set(obj.x)
            node.appendListNode().set(obj.y)
            node.appendListNode().set(obj.z)
            node.appendListNode().set(obj.w)
        }
    }

    override fun deserialize(type: Type, node: ConfigurationNode): Quat {
        fun usage(): Nothing = throw SerializationException(node, type, "Quat must be expressed as [x, y, z, w] or [order, x, y, z]")

        if (!node.isList) usage()
        val list = node.childrenList()
        if (list.size != 4) usage()

        if (list[0].raw() is String) {
            return Vec3(
                list[1].force(),
                list[2].force(),
                list[3].force()
            ).radians().quat(list[0].force())
        } else {
            return Quat(
                list[0].force(),
                list[1].force(),
                list[2].force(),
                list[3].force(),
            )
        }
    }
}
