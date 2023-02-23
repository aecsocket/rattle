package io.github.aecsocket.ignacio.core.serializer

import io.github.aecsocket.alexandria.core.extension.force
import io.github.aecsocket.ignacio.core.math.*
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.serialize.SerializationException
import org.spongepowered.configurate.serialize.TypeSerializer
import java.lang.reflect.Type

private const val VECTOR_ARGS = "Vector must be expressed as [x, y, z]"

object Vec3fSerializer : TypeSerializer<Vec3f> {
    override fun serialize(type: Type, obj: Vec3f?, node: ConfigurationNode) {
        if (obj == null) node.set(null)
        else {
            node.appendListNode().set(obj.x)
            node.appendListNode().set(obj.y)
            node.appendListNode().set(obj.z)
        }
    }

    override fun deserialize(type: Type, node: ConfigurationNode): Vec3f {
        if (node.isList) {
            val list = node.childrenList()
            if (list.size < 3)
                throw SerializationException(node, type, VECTOR_ARGS)
            return Vec3f(
                list[0].force(),
                list[1].force(),
                list[2].force(),
            )
        } else {
            return Vec3f(node.force())
        }
    }
}

object Vec3dSerializer : TypeSerializer<Vec3d> {
    override fun serialize(type: Type, obj: Vec3d?, node: ConfigurationNode) {
        if (obj == null) node.set(null)
        else {
            node.appendListNode().set(obj.x)
            node.appendListNode().set(obj.y)
            node.appendListNode().set(obj.z)
        }
    }

    override fun deserialize(type: Type, node: ConfigurationNode): Vec3d {
        if (node.isList) {
            val list = node.childrenList()
            if (list.size != 3)
                throw SerializationException(node, type, VECTOR_ARGS)
            return Vec3d(
                list[0].force(),
                list[1].force(),
                list[2].force(),
            )
        } else {
            return Vec3d(node.force())
        }
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
        val list = node.childrenList()
        if (list.size != 4)
            throw SerializationException(node, type, "Quat must be expressed as [x, y, z, w] or [order, x, y, z]")
        return when (list[0].raw()) {
            is String -> {
                val order = list[0].force<EulerOrder>()
                Vec3f(list[1].force(), list[2].force(), list[3].force())
                    .radians().quat(order)
            }
            else -> Quat(list[0].force(), list[1].force(), list[2].force(), list[3].force())
        }
    }
}
