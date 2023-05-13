package io.github.aecsocket.ignacio.physx

import io.github.aecsocket.ignacio.Shape
import physx.physics.PxShape

class PhysxShape internal constructor(
    val shape: PxShape,
) : Shape {
    override val refCount: Long
        get() = shape.referenceCount.toLong()

    override fun increment() {
        shape.acquireReference()
    }

    override fun decrement() {
        shape.release()
    }
}
