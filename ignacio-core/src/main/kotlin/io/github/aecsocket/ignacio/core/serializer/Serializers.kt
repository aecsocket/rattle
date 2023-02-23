package io.github.aecsocket.ignacio.core.serializer

import io.github.aecsocket.alexandria.core.extension.registerExact
import org.spongepowered.configurate.serialize.TypeSerializerCollection

val ignacioCoreSerializers = TypeSerializerCollection.builder()
    .registerExact(Vec3fSerializer)
    .registerExact(Vec3dSerializer)
    .registerExact(QuatSerializer)
    .build()
