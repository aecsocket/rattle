package io.github.aecsocket.ignacio.core.util

import io.leangen.geantyref.TypeToken
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.serialize.SerializationException
import java.lang.reflect.Type
import kotlin.reflect.KClass

inline fun <reified T> typeToken() = object : TypeToken<T>() {}

fun ConfigurationNode.force(type: Type) = get(type)
    ?: throw SerializationException(this, type, "A value is required for this field")

fun <V : Any> ConfigurationNode.force(type: KClass<V>) = get(type.java)
    ?: throw SerializationException(this, type.java, "A value is required for this field")

fun <V : Any> ConfigurationNode.force(type: TypeToken<V>) = get(type)
    ?: throw SerializationException(this, type.type, "A value is required for this field")

inline fun <reified V : Any> ConfigurationNode.force() = force(typeToken<V>())
