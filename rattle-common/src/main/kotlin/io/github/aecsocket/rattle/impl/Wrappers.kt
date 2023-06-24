package io.github.aecsocket.rattle.impl

import io.github.aecsocket.klam.*

data class Location<W>(
    val world: W,
    val position: DVec3,
)

interface Entity {
    val position: DIso3
}
