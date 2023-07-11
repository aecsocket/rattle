package io.github.aecsocket.rattle.serializer

import io.github.aecsocket.alexandria.extension.registerExact
import io.github.aecsocket.rattle.world.terrainLayerSerializer
import org.spongepowered.configurate.serialize.TypeSerializerCollection

val rattleSerializers: TypeSerializerCollection =
    TypeSerializerCollection.builder().registerExact(terrainLayerSerializer).build()
