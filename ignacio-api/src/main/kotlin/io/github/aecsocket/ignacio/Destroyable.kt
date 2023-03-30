package io.github.aecsocket.ignacio

import java.util.concurrent.atomic.AtomicBoolean

interface Destroyable {
    fun destroy()
}

class DestroyFlag {
    private val destroyed = AtomicBoolean(false)

    fun mark() {
        if (destroyed.getAndSet(true))
            throw IllegalStateException("Object is already destroyed")
    }
}
