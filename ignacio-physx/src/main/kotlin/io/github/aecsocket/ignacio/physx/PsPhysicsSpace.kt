package io.github.aecsocket.ignacio.physx

import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.math.Transform
import physx.physics.PxRigidDynamic
import physx.physics.PxRigidStatic
import physx.physics.PxScene

class PsPhysicsSpace(
    private val engine: PhysxEngine,
    val handle: PxScene
) : PhysicsSpace {
    fun destroy() {
        handle.release()
    }

    override fun addStaticBody(geometry: Geometry, transform: Transform): StaticBody {
        val body: PxRigidStatic
        pushMemory {
            body = engine.physics.createRigidStatic(pxTransform(transform))
        }
        return PsStaticBody(engine, body)
    }

    override fun addDynamicBody(geometry: Geometry, transform: Transform): DynamicBody {
        val body: PxRigidDynamic
        pushMemory {
            body = engine.physics.createRigidDynamic(pxTransform(transform))
        }
        return PsDynamicBody(engine, body)
    }

    override fun removeBody(body: PhysicsBody) {
        body as PsBody
        handle.removeActor(body.handle)
    }

    override fun update(deltaTime: Float) {
        handle.simulate(deltaTime)
        handle.fetchResults(true)
    }
}
