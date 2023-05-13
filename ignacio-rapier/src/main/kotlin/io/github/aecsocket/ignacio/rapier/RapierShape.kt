package io.github.aecsocket.ignacio.rapier

import io.github.aecsocket.ignacio.Shape
import rapier.shape.SharedShape

class RapierShape internal constructor(
    val shape: SharedShape
) : Shape {
    override val refCount: Long
        get() = shape.strongCount()

    override fun increment() {
    }

    override fun decrement() {

    }
}
