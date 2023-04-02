package io.github.aecsocket.ignacio

import java.util.concurrent.atomic.AtomicBoolean

interface Destroyable {
    fun destroy()
}

class DestroyFlag {
    private val marked = AtomicBoolean(false)

    fun marked() = marked.get()

    fun mark() {
        if (marked.getAndSet(true))
            throw IllegalStateException("Object is already destroyed")
    }
}
