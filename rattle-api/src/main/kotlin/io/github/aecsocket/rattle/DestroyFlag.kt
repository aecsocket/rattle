package io.github.aecsocket.rattle

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Utility class for disallowing double-free in native resources.
 */
class DestroyFlag {
    private val destroyed = AtomicBoolean(false)

    fun get() = destroyed.get()

    operator fun invoke() {
        if (destroyed.getAndSet(true))
            throw IllegalStateException("Object is already destroyed")
    }
}
