package io.github.aecsocket.ignacio.physx

import io.github.aecsocket.ignacio.core.DynamicBody
import io.github.aecsocket.ignacio.core.PhysicsBody
import io.github.aecsocket.ignacio.core.StaticBody
import io.github.aecsocket.ignacio.core.math.Transform
import physx.physics.PxRigidActor
import physx.physics.PxRigidDynamic
import physx.physics.PxRigidStatic

sealed class PsBody(
    private val engine: PhysxEngine,
    open val handle: PxRigidActor
) : PhysicsBody {
    override var transform: Transform
        get() = handle.globalPose.ignacio()
        set(value) {
            useMemory {
                handle.globalPose = pxTransform(value)
            }
        }

    override fun destroy() {
        // todo
    }
}

class PsStaticBody(
    engine: PhysxEngine, override val handle: PxRigidStatic
) : PsBody(engine, handle), StaticBody

class PsDynamicBody(
    engine: PhysxEngine, override val handle: PxRigidDynamic
) : PsBody(engine, handle), DynamicBody
