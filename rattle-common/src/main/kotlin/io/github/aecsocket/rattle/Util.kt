package io.github.aecsocket.rattle

import io.github.aecsocket.klam.*

interface CommandSource

interface World

data class Location(
    val world: World,
    val position: DVec3,
)
