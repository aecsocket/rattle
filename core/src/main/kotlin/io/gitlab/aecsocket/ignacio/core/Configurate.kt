package io.gitlab.aecsocket.ignacio.core

import io.leangen.geantyref.TypeToken
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.serialize.SerializationException
import java.lang.reflect.Type
import kotlin.reflect.KClass

inline fun <reified T> igTypeToken() = object : TypeToken<T>() {}

fun ConfigurationNode.igForce(type: Type) = get(type)
    ?: throw SerializationException(this, type, "A value is required for this field")

fun <V : Any> ConfigurationNode.igForce(type: KClass<V>) = get(type.java)
    ?: throw SerializationException(this, type.java, "A value is required for this field")

fun <V : Any> ConfigurationNode.igForce(type: TypeToken<V>) = get(type)
    ?: throw SerializationException(this, type.type, "A value is required for this field")

inline fun <reified V : Any> ConfigurationNode.igForce() = igForce(igTypeToken<V>())
