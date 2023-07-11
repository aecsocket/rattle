package io.github.aecsocket.rattle

import java.util.concurrent.atomic.AtomicBoolean

/** Utility class for disallowing double-free in native resources. */
class DestroyFlag {
  private val destroyed = AtomicBoolean(false)

  /** If this flag has been marked as destroyed yet. */
  fun get() = destroyed.get()

  /** Marks this flag as destroyed. */
  operator fun invoke() {
    if (destroyed.getAndSet(true)) throw IllegalStateException("Object is already destroyed")
  }
}
