package io.github.aecsocket.ignacio.core.math

import io.github.aecsocket.ignacio.core.util.force
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
            if (list.size < 3)
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
