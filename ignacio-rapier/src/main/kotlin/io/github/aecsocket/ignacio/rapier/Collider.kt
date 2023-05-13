package io.github.aecsocket.ignacio.rapier

import io.github.aecsocket.ignacio.Collider
import io.github.aecsocket.ignacio.DestroyFlag
import io.github.aecsocket.ignacio.RigidBody

@JvmInline
value class ColliderHandle(val id: Long)

data class RapierCollider internal constructor(
    val space: RapierSpace,
    val handle: ColliderHandle,
) : Collider {
    private val destroyed = DestroyFlag()

    override fun destroy() {
        destroyed()
        val collider = space.colliderSet.remove(
            handle.id,
            space.islands,
            space.rigidBodySet,
            false,
        ) ?: return
        collider.drop()
    }

    override fun detachFromParent() {
        space.colliderSet.setParent(
            handle.id,
            null,
            space.rigidBodySet,
        )
    }

    override fun attachTo(parent: RigidBody) {
        parent as RapierRigidBody
        space.colliderSet.setParent(
            handle.id,
            parent.handle.id,
            space.rigidBodySet,
        )
    }
}
