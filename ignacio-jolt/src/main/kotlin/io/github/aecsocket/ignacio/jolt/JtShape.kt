package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.core.DestroyFlag
import io.github.aecsocket.ignacio.core.Shape

class JtShape(val handle: JShape) : Shape {
    private val destroyed = DestroyFlag()

    override fun destroy() {
        destroyed.mark()
        handle.destroy()
    }
}
