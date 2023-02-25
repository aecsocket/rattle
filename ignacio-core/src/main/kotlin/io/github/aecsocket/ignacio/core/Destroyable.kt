package io.github.aecsocket.ignacio.core

interface Destroyable {
    fun destroy()
}

fun <T : Destroyable, R> T.use(block: (T) -> R): R {
    val result = block(this)
    destroy()
    return result
}
