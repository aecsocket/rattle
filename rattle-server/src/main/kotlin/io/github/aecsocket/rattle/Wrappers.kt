package io.github.aecsocket.rattle

import net.kyori.adventure.key.Key

interface World {
    val key: Key
}

data class Location(
    val world: World,
    val position: Vec,
)
