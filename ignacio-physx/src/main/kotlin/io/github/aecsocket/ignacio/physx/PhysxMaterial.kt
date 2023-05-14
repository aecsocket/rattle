package io.github.aecsocket.ignacio.physx

import io.github.aecsocket.ignacio.DestroyFlag
import io.github.aecsocket.ignacio.PhysicsMaterial
import physx.physics.PxMaterial

class PhysxMaterial internal constructor(
    val material: PxMaterial,
) : PhysicsMaterial {
    private val destroyed = DestroyFlag()

    override fun destroy() {
        destroyed()
        material.release()
    }
}
