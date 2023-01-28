package io.gitlab.aecsocket.ignacio.paper

import net.kyori.adventure.key.Key
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Required
import org.spongepowered.configurate.serialize.SerializationException
import org.spongepowered.configurate.serialize.TypeSerializer
import java.lang.reflect.Type

@ConfigSerializable
data class IgItemDescriptor(
    @Required val material: Material,
    val modelData: Int = 0,
    val damage: Int = 0
) {
    fun createItem(): ItemStack {
        val item = ItemStack(material)
        item.editMeta { meta ->
            meta.setCustomModelData(modelData)
            if (meta is Damageable) meta.damage = damage
        }
        return item
    }
}

internal object MaterialSerializer : TypeSerializer<Material> {
    override fun serialize(type: Type, obj: Material?, node: ConfigurationNode) {
        if (obj == null) node.set(null)
        else {
            node.set(obj.key)
        }
    }

    override fun deserialize(type: Type, node: ConfigurationNode): Material {
        val key = node.get<Key>()
            ?: throw SerializationException(node, type, "Material must be expressed as namespaced key")
        return Registry.MATERIAL[NamespacedKey(key.namespace(), key.value())]
            ?: throw SerializationException(node, type, "Invalid Material $key")
    }
}
