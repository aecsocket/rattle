package io.github.aecsocket.rattle.impl

import io.github.aecsocket.klam.DVec3
import io.github.aecsocket.rattle.Iso

data class Location<W>(
    val world: W,
    val position: DVec3,
)

interface Entity {
    val position: Iso
}
