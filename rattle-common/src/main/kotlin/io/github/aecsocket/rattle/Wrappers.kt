package io.github.aecsocket.rattle

import io.github.aecsocket.klam.DVec3

data class Location<W>(
    val world: W,
    val position: DVec3,
)

sealed interface Block {
    /* TODO: Kotlin 1.9 data */ object Passable : Block

    sealed interface Shaped : Block {
        val shape: Shape
    }

    data class Solid(override val shape: Shape) : Shaped

    data class Fluid(override val shape: Shape) : Shaped
}
