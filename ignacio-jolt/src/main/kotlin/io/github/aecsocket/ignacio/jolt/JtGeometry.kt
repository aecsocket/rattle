package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.core.DestroyFlag
import jolt.physics.collision.shape.Shape

class JtGeometry(val handle: Shape) : io.github.aecsocket.ignacio.core.Geometry {
    private val destroyed = DestroyFlag()

    override fun destroy() {
        destroyed.mark()
        handle.destroy()
    }
}
