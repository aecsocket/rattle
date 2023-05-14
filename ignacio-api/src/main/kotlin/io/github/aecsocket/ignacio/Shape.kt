package io.github.aecsocket.ignacio

interface Shape : RefCounted {
    override fun acquire(): Shape

    override fun release(): Shape
}
