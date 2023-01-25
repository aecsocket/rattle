package io.gitlab.aecsocket.ignacio.paper

import io.leangen.geantyref.TypeToken
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.serialize.SerializationException
import java.lang.reflect.Type
import kotlin.reflect.KClass

internal inline fun <reified T> typeToken() = object : TypeToken<T>() {}

internal fun <V> ConfigurationNode.getIfExists(type: TypeToken<V>): V? = if (virtual()) null else get(type)

internal inline fun <reified V> ConfigurationNode.getIfExists() = getIfExists(typeToken<V>())

internal fun ConfigurationNode.force(type: Type) = get(type)
    ?: throw SerializationException(this, type, "A value is required for this field")

internal fun <V : Any> ConfigurationNode.force(type: KClass<V>) = get(type.java)
    ?: throw SerializationException(this, type.java, "A value is required for this field")

internal fun <V : Any> ConfigurationNode.force(type: TypeToken<V>) = get(type)
    ?: throw SerializationException(this, type.type, "A value is required for this field")

internal inline fun <reified V : Any> ConfigurationNode.force() = force(typeToken<V>())
