package io.github.aecsocket.ignacio.physx

import io.github.aecsocket.ignacio.PhysicsSpace
import physx.physics.PxScene

class PhysxSpace internal constructor(
    val scene: PxScene,
) : PhysicsSpace {

}
