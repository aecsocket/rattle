package io.gitlab.aecsocket.ignacio.core

import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.serialize.SerializationException
import org.spongepowered.configurate.serialize.TypeSerializer
import java.lang.reflect.Type

data class Vec3(val x: IgScalar, val y: IgScalar, val z: IgScalar) {
    companion object {
        val Zero = Vec3(0.0)
    }

    constructor(n: IgScalar) : this(n, n, n)

    @JvmName("add")
    operator fun plus(v: Vec3) = Vec3(x+v.x, y+v.y, z+v.z)
    @JvmName("add")
    operator fun plus(s: IgScalar) = Vec3(x+s, y+s, z+s)

    @JvmName("sub")
    operator fun minus(v: Vec3) = Vec3(x-v.x, y-v.y, z-v.z)
    @JvmName("sub")
    operator fun minus(s: IgScalar) = Vec3(x-s, y-s, z-s)

    fun asString(fmt: String = "%f") = "($fmt, $fmt, $fmt)".format(x, y, z)

    override fun toString() = asString("%.3f")
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
