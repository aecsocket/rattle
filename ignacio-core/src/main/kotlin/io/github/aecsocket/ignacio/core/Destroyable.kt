package io.github.aecsocket.ignacio.core

interface Destroyable : AutoCloseable {
    fun destroy()

    override fun close() = destroy()
}
