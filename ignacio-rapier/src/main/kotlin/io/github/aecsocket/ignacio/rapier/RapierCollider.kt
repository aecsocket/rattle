package io.github.aecsocket.ignacio.rapier

import io.github.aecsocket.ignacio.Collider
import io.github.aecsocket.ignacio.DestroyFlag

@JvmInline
value class ColliderHandle(val id: Long)

class RapierCollider internal constructor(
    val space: RapierSpace,
    val handle: ColliderHandle,
) : Collider {
    private val destroyed = DestroyFlag()

    override fun destroy() {
        destroyed()
        val coll = space.colliderSet.remove(
            handle.id,
            space.islands,
            space.rigidBodySet,
            false,
        ) ?: return
        coll.drop()
    }
}
