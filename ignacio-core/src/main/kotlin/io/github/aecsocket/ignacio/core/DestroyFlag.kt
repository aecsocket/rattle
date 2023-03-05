package io.github.aecsocket.ignacio.core

class DestroyFlag {
    private var destroyed = false

    fun mark() {
        if (destroyed) throw IllegalStateException("Already destroyed")
        destroyed = true
    }
}
