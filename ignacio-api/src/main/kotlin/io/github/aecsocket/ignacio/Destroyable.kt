package io.github.aecsocket.ignacio

import java.util.concurrent.atomic.AtomicBoolean

interface Destroyable {
    fun destroy()
}

class DestroyFlag {
    private val marked = AtomicBoolean(false)
//    private var destroyedAt: Array<StackTraceElement>? = null

    fun marked() = marked.get()

    fun mark() {
        if (marked.getAndSet(true)) {
//            destroyedAt?.forEach { println(it) }
            throw IllegalStateException("Object is already destroyed")
        }
//        destroyedAt = Thread.currentThread().stackTrace
    }
}
