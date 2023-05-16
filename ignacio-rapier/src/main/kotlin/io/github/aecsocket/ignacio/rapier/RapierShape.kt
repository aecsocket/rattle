package io.github.aecsocket.ignacio.rapier

import io.github.aecsocket.ignacio.RefCounted
import io.github.aecsocket.ignacio.Shape
import rapier.shape.SharedShape

class RapierShape internal constructor(
    val handle: SharedShape
) : Shape, RefCounted {
    override val refCount: Long
        get() = handle.strongCount()

    override fun acquire(): RapierShape {
        handle.acquire()
        return this
    }

    override fun release(): RapierShape {
        handle.release()
        return this
    }

    override fun destroy() {
        release()
    }
}
