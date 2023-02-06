package io.github.aecsocket.ignacio.physx

import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.math.Transform
import physx.common.PxTransform
import physx.physics.PxRigidActor
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

    private fun <B : PxRigidActor> addBody(
        geometry: Geometry,
        transform: Transform,
        creator: (PxTransform) -> B
    ): B {
        val body: B
        useMemory {
            body = creator(pxTransform(transform))
            body.attachShape(engine.createShape(geometry, Transform.Identity))
        }
        handle.addActor(body)
        return body
    }

    override fun addStaticBody(geometry: Geometry, transform: Transform): StaticBody {
        val body = addBody(geometry, transform, engine.physics::createRigidStatic)
        return PsStaticBody(engine, body)
    }

    override fun addDynamicBody(geometry: Geometry, transform: Transform, dynamics: BodyDynamics): DynamicBody {
        val body = addBody(geometry, transform, engine.physics::createRigidDynamic)
        if (dynamics.activate)
            body.wakeUp()
        else
            body.putToSleep()
        body.mass = dynamics.mass
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
