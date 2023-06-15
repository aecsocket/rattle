package io.github.aecsocket.rattle

import java.util.function.Consumer

interface EventDelegate<E> {
    operator fun invoke(listener: Consumer<E>)

    interface Mut<E> : EventDelegate<E> {
        operator fun <T : E> invoke(event: T): T
    }
}

class EventDelegateImpl<E> internal constructor() : EventDelegate.Mut<E> {
    private val listeners = ArrayList<Consumer<E>>()

    override fun invoke(listener: Consumer<E>) {
        listeners += listener
    }

    override fun <T : E> invoke(event: T): T {
        listeners.forEach { it.accept(event) }
        return event
    }
}

@Suppress("FunctionName")
fun <E> EventDelegate(): EventDelegate.Mut<E> = EventDelegateImpl()
