package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.BodyAccess
import io.github.aecsocket.ignacio.core.math.Ray
import io.github.aecsocket.ignacio.core.math.Transform
import io.github.aecsocket.ignacio.core.math.Vec3d
import jolt.core.TempAllocator
import jolt.kotlin.*
import jolt.math.JtMat44f
import jolt.physics.Activation
import jolt.physics.PhysicsSystem
import jolt.physics.body.BodyFilter
import jolt.physics.body.MassProperties
import jolt.physics.body.MotionType
import jolt.physics.body.OverrideMassProperties
import jolt.physics.collision.*
import jolt.physics.collision.broadphase.BroadPhaseLayerFilter
import jolt.physics.collision.broadphase.CollideShapeBodyCollector
import jolt.physics.collision.shape.CastRayCollector

class JtPhysicsSpace(
    private val engine: JoltEngine,
    val handle: PhysicsSystem,
    val tempAllocator: TempAllocator,
    settings: PhysicsSpace.Settings,
) : PhysicsSpace {
    override var settings = settings
        set(value) {
            field = value
            handle.gravity = value.gravity.jolt()
        }

    private fun bodyAccess(id: BodyID) = JtBodyAccess(handle, id)

    override val bodies = object : PhysicsSpace.Bodies {
        override fun createStaticBody(snapshot: StaticBodySnapshot, transform: Transform): JtStaticBodyAccess {
            val shape = engine.createShape(snapshot.geometry)
            return BodyCreationSettings(
                shape,
                transform.position.jolt(),
                transform.rotation.jolt(),
                MotionType.STATIC,
                objectLayerNonMoving
            ).use { settings ->
                val body = handle.bodyInterface.createBody(settings)
                bodyAccess(body.bodyId).asStatic()
            }
        }

        override fun createDynamicBody(snapshot: DynamicBodySnapshot, transform: Transform): JtDynamicBodyAccess {
            val shape = engine.createShape(snapshot.geometry)
            return BodyCreationSettings(
                shape,
                transform.position.jolt(),
                transform.rotation.jolt(),
                MotionType.DYNAMIC,
                objectLayerMoving
            ).use { settings ->
                settings.overrideMassProperties = OverrideMassProperties.CALCULATE_INERTIA
                settings.massPropertiesOverride = MassProperties(snapshot.mass, JtMat44f())
                settings.linearVelocity = snapshot.linearVelocity.jolt()
                settings.angularVelocity = snapshot.angularVelocity.jolt()
                settings.friction = snapshot.friction
                settings.restitution = snapshot.restitution
                settings.linearDamping = snapshot.linearDamping
                settings.angularDamping = snapshot.angularDamping
                settings.maxLinearVelocity = snapshot.maxLinearVelocity
                settings.maxAngularVelocity = snapshot.maxAngularVelocity
                settings.gravityFactor = snapshot.gravityFactor
                val body = handle.bodyInterface.createBody(settings)
                bodyAccess(body.bodyId).asDynamic()
            }
        }

        override fun destroyBody(body: BodyAccess) {
            body as JtBodyAccess
            if (handle.bodyInterface.isAdded(body.id)) {
                handle.bodyInterface.removeBody(body.id)
            }
            handle.bodyInterface.destroyBody(body.id)
        }

        override fun addBody(body: BodyAccess, activate: Boolean) {
            body as JtBodyAccess
            handle.bodyInterface.addBody(body.id, Activation.ofValue(activate))
        }

        override fun removeBody(body: BodyAccess) {
            body as JtBodyAccess
            handle.bodyInterface.removeBody(body.id)
        }
    }

    override val broadQuery = object : PhysicsSpace.BroadQuery {
        override fun overlapSphere(position: Vec3d, radius: Float): Collection<JtBodyAccess> {
            val hits = ArrayList<BodyID>()
            val collector = object : CollideShapeBodyCollector() {
                override fun addHit(result: Int) {
                    hits += BodyID(result)
                }
            }
            // TODO filters
            handle.broadPhaseQuery.collideSphere(position.joltSp(), radius, collector, BroadPhaseLayerFilter.passthrough(), ObjectLayerFilter.passthrough())
            return hits.map { bodyAccess(it) }
        }
    }

    override val narrowQuery = object : PhysicsSpace.NarrowQuery {
        val rayCastSettings = RayCastSettings(BackFaceMode.IGNORE_BACK_FACES, true)

        override fun rayCastBody(ray: Ray, distance: Float): PhysicsSpace.RayCast? {
            val hit = RayCastResult()
            val result = handle.narrowPhaseQuery.castRayDp(
                RayCast3d(ray.origin.jolt(), ray.direction.jolt()),
                hit,
                // TODO filters
                BroadPhaseLayerFilter.passthrough(),
                ObjectLayerFilter.passthrough(),
                BodyFilter.passthrough(),
            )
            return if (result) {
                PhysicsSpace.RayCast(bodyAccess(BodyID(hit.bodyId)))
            } else null
        }

        override fun rayCastBodies(ray: Ray, distance: Float): Collection<BodyAccess> {
            val hits = ArrayList<BodyID>()
            val collector = object : CastRayCollector() {
                override fun addHit(result: RayCastResult) {
                    hits += BodyID(result.bodyId)
                }
            }
            handle.narrowPhaseQuery.castRayDp(
                RayCast3d(ray.origin.jolt(), ray.direction.jolt()),
                rayCastSettings,
                collector,
                // TODO filters
                BroadPhaseLayerFilter.passthrough(),
                ObjectLayerFilter.passthrough(),
                BodyFilter.passthrough(),
                ShapeFilter.passthrough(),
            )
            return hits.map { bodyAccess(it) }
        }
    }

    override val numBodies get() = handle.numBodies
    override val numActiveBodies get() = handle.numActiveBodies

    fun destroy() {
        // TODO delete bodies
        handle.delete()
        tempAllocator.delete()
    }

    override fun update(deltaTime: Float) {
        handle.update(
            deltaTime,
            engine.settings.spaces.collisionSteps,
            engine.settings.spaces.integrationSubSteps,
            tempAllocator,
            engine.jobSystem
        )
    }
}
