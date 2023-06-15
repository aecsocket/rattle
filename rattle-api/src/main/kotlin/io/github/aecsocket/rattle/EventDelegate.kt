package io.github.aecsocket.rattle

import java.util.function.Consumer

/**
 * An interface for registering event listeners for the event type [E].
 *
 * To create an instance, use `EventDelegate()`.
 */
interface EventDelegate<E> {
    /**
     * Registers a listener.
     * @param priority When this listener will be run in relation to other listeners.
     *                 A lower priority runs earlier; a higher priority runs later and has the "final say" on the event.
     * @param listener The listener function to run.
     */
    operator fun invoke(priority: Int = 0, listener: Consumer<E>)

    /**
     * An [EventDelegate] which can have the event called on it.
     */
    interface Own<E> : EventDelegate<E> {
        /**
         * Calls the event [E], or any subtype [T], allowing all registered listeners to view and/or modify it.
         * Returns the [T] that was passed in.
         */
        operator fun <T : E> invoke(event: T): T
    }
}

class EventDelegateImpl<E> internal constructor() : EventDelegate.Own<E> {
    private class Listener<E>(
        val priority: Int,
        val fn: Consumer<E>,
    )

    private val listeners = ArrayList<Listener<E>>()

    override fun invoke(priority: Int, listener: Consumer<E>) {
        listeners += Listener(
            priority = priority,
            fn = listener,
        )
        listeners.sortBy { it.priority }
    }

    override fun <T : E> invoke(event: T): T {
        listeners.forEach { it.fn.accept(event) }
        return event
    }
}

/**
 * Creates a new [EventDelegate.Own] instance with no registered listeners.
 */
@Suppress("FunctionName")
fun <E> EventDelegate(): EventDelegate.Own<E> = EventDelegateImpl()
