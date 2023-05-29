package io.github.aecsocket.rattle

import io.github.aecsocket.klam.DVec3

data class Location<W>(
    val world: W,
    val position: DVec3,
)
