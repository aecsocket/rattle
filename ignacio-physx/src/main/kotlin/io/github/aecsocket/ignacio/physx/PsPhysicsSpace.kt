package io.github.aecsocket.ignacio.physx

import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.math.Ray
import io.github.aecsocket.ignacio.core.math.Transform
import io.github.aecsocket.ignacio.core.math.Vec3d
import physx.common.PxTransform
import physx.physics.*

class PsPhysicsSpace(
    private val engine: PhysxEngine,
    val handle: PxScene
) : PhysicsSpace {
    val bodies = HashMap<PxActor, PsBody>()

    override val numBodies get() = bodies.size
    override val numActiveBodies get() = bodies.count { (actor) ->
        actor is PxRigidDynamic && !actor.isSleeping
    }

    fun destroy() {
        bodies.forEach { (actor) ->
            handle.removeActor(actor, false)
        }
        handle.release()
    }

    fun bodyOf(actor: PxActor) = bodies[actor]
        ?: throw IllegalStateException("Actor $actor does not have a corresponding Ignacio wrapper")

    private fun <B : PxRigidActor> addBody(
        geometry: Geometry,
        transform: Transform,
        createBody: (PxTransform) -> B
    ): B {
        val body: B
        useMemory {
            body = createBody(pxTransform(transform))
            body.attachShape(engine.createShape(geometry, Transform.Identity))
        }
        handle.addActor(body)
        return body
    }

    override fun addStaticBody(geometry: Geometry, transform: Transform): StaticBody {
        val body = addBody(geometry, transform, engine.physics::createRigidStatic)
        return PsStaticBody(engine, body).also {
            bodies[body] = it
        }
    }

    override fun addDynamicBody(geometry: Geometry, transform: Transform, dynamics: BodyDynamics): DynamicBody {
        val body = addBody(geometry, transform, engine.physics::createRigidDynamic)
        if (dynamics.activate)
            body.wakeUp()
        else
            body.putToSleep()
        body.mass = dynamics.mass
        return PsDynamicBody(engine, body).also {
            bodies[body] = it
        }
    }

    override fun removeBody(body: PhysicsBody) {
        body as PsBody
        bodies.remove(body.handle)
        handle.removeActor(body.handle)
    }

    override fun rayCastBodies(ray: Ray, distance: Float): Collection<PhysicsBody> {
        val callback = PxRaycastResult()
        useMemory {
            handle.raycast(pxVec3(ray.origin), pxVec3(ray.direction), distance, callback)
        }
        return (0 until callback.nbAnyHits).map {
            bodyOf(callback.getAnyHit(it).actor)
        }
    }

    override fun bodiesNear(position: Vec3d, radius: Float): Collection<PhysicsBody> {
        val callback = PxOverlapResult()
        useMemory {
            handle.overlap(pxSphereGeometry(radius), pxTransform(pxVec3(position), pxQuat()), callback)
        }
        return (0 until callback.nbAnyHits).map {
            bodyOf(callback.getAnyHit(it).actor)
        }
    }

    override fun update(deltaTime: Float) {
        handle.simulate(deltaTime)
        handle.fetchResults(true)
    }
}
