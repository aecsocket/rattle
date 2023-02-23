package io.github.aecsocket.ignacio.core

import io.github.aecsocket.alexandria.core.extension.warning
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

private const val JOIN_TIMEOUT = 5000L

class PhysicsThread(
    engineName: String,
    private val logger: Logger
) : Thread("Physics-$engineName"), Destroyable, Executor {
    private val running = AtomicBoolean(true)
    private val tasks = ConcurrentLinkedQueue<Runnable>()

    override fun run() {
        while (running.get()) {
            while (tasks.isNotEmpty()) {
                try {
                    tasks.poll().run()
                } catch (ex: Exception) {
                    logger.warning("Physics task threw an exception", ex)
                }
            }
        }
    }

    override fun execute(command: Runnable) {
        tasks += command
    }

    override fun destroy() {
        running.set(false)

        try {
            join(JOIN_TIMEOUT)
        } catch (ex: InterruptedException) {
            logger.warning("Could not join physics thread in $JOIN_TIMEOUT ms", ex)
        }
    }
}
