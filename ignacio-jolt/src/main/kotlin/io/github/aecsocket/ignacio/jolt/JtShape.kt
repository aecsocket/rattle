package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.DestroyFlag
import io.github.aecsocket.ignacio.Shape

data class JtShape internal constructor(val handle: jolt.physics.collision.shape.Shape) : Shape {
    private val destroyed = DestroyFlag()

    override fun destroy() {
        destroyed.mark()
        handle.delete()
    }
}
