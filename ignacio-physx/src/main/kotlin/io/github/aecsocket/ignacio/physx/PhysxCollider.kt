package io.github.aecsocket.ignacio.physx

import io.github.aecsocket.ignacio.Collider
import io.github.aecsocket.ignacio.DestroyFlag
import physx.physics.PxShape

class PhysxCollider internal constructor(
    val shape: PxShape,
) : Collider {
    private val destroyed = DestroyFlag()

    override fun destroy() {
        destroyed()
        shape.release()
    }
}
