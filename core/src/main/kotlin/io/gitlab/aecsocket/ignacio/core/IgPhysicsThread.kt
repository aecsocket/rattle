package io.gitlab.aecsocket.ignacio.core

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

class IgPhysicsThread(private val logger: Logger) : Thread("Physics thread"), Executor {
    private val running = AtomicBoolean(true)
    private val tasks = ConcurrentLinkedQueue<Runnable>()

    override fun run() {
        while (running.get()) {
            while (tasks.isNotEmpty()) {
                try {
                    tasks.poll().run()
                } catch (ex: Exception) {
                    logger.warning("Physics thread task threw an exception")
                    ex.printStackTrace()
                }
            }
        }
    }

    override fun execute(command: Runnable) {
        tasks.add(command)
    }

    fun assertThread() {
        if (currentThread() !== this)
            throw IllegalStateException("Must run physics operation on physics thread")
    }

    fun destroy() {
        running.set(false)

        try {
            join(5000)
        } catch (ex: InterruptedException) {
            logger.warning("Could not wait for physics thread to join")
            ex.printStackTrace()
        }
    }
}
