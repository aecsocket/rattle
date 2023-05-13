package io.github.aecsocket.ignacio

import java.util.concurrent.atomic.AtomicBoolean

class DestroyFlag {
    private val destroyed = AtomicBoolean(false)

    operator fun invoke() {
        if (destroyed.getAndSet(true))
            throw IllegalStateException("Object is already destroyed")
    }
}
