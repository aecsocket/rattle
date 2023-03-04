package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.BodyAccess
import io.github.aecsocket.ignacio.core.math.Ray
import io.github.aecsocket.ignacio.core.math.Transform
import io.github.aecsocket.ignacio.core.math.Vec3d
import io.github.aecsocket.ignacio.core.math.f
import jolt.core.TempAllocator
import jolt.kotlin.*
import jolt.physics.Activation
import jolt.physics.PhysicsSystem
import jolt.physics.body.MotionType
import jolt.physics.collision.*
import jolt.physics.collision.broadphase.BroadPhaseLayerFilter
import jolt.physics.collision.broadphase.CollideShapeBodyCollector
import jolt.physics.collision.broadphase.RayCastBodyCollector
import java.lang.foreign.MemorySession

class JtPhysicsSpace(
    private val engine: JoltEngine,
    val handle: PhysicsSystem,
    val tempAllocator: TempAllocator,
    settings: PhysicsSpace.Settings,
) : PhysicsSpace {
    override var settings = settings
        set(value) {
            field = value
            useArena {
                handle.setGravity(value.gravity.toJolt())
            }
        }

    private fun bodyAccess(id: BodyId) = JtBodyAccess(handle, id)

    override val bodies = object : PhysicsSpace.Bodies {
        override fun createStatic(settings: StaticBodySettings, transform: Transform): JtStaticBodyAccess {
            val shape = engine.createShape(settings.geometry)
            return useArena {
                val bodySettings = BodyCreationSettings(this,
                    shape,
                    transform.position.toJolt(),
                    transform.rotation.toJolt(),
                    MotionType.STATIC,
                    objectLayerNonMoving
                )
                val body = handle.bodyInterface.createBody(bodySettings)
                bodyAccess(body.bodyId).asStatic()
            }
        }

        override fun createDynamic(settings: DynamicBodySettings, transform: Transform): JtDynamicBodyAccess {
            val shape = engine.createShape(settings.geometry)
            return useArena {
                val bodySettings = BodyCreationSettings(this,
                    shape,
                    transform.position.toJolt(),
                    transform.rotation.toJolt(),
                    MotionType.DYNAMIC,
                    objectLayerMoving
                )
                // TODO settings
                /*

                settings.overrideMassProperties = OverrideMassProperties.CALCULATE_INERTIA
                settings.massPropertiesOverride = MassProperties(snapshot.mass, JtMat44f())
                settings.linearVelocity = snapshot.linearVelocity.toJolt()
                settings.angularVelocity = snapshot.angularVelocity.toJolt()
                settings.friction = snapshot.friction
                settings.restitution = snapshot.restitution
                settings.linearDamping = snapshot.linearDamping
                settings.angularDamping = snapshot.angularDamping
                settings.maxLinearVelocity = snapshot.maxLinearVelocity
                settings.maxAngularVelocity = snapshot.maxAngularVelocity
                settings.gravityFactor = snapshot.gravityFactor
                 */
                val body = handle.bodyInterface.createBody(bodySettings)
                bodyAccess(body.bodyId).asDynamic()
            }
        }

        override fun destroy(body: BodyAccess) {
            body as JtBodyAccess
            if (handle.bodyInterface.isAdded(body.id.id)) {
                handle.bodyInterface.removeBody(body.id.id)
            }
            handle.bodyInterface.destroyBody(body.id.id)
        }

        override fun add(body: BodyAccess, activate: Boolean) {
            body as JtBodyAccess
            handle.bodyInterface.addBody(body.id.id, Activation.ofValue(activate))
        }

        override fun addStatic(settings: StaticBodySettings, transform: Transform): StaticBodyAccess {
            val body = createStatic(settings, transform)
            add(body, false)
            return body
        }

        override fun addDynamic(
            settings: DynamicBodySettings,
            transform: Transform,
            activate: Boolean
        ): DynamicBodyAccess {
            val body = createDynamic(settings, transform)
            add(body, activate)
            return body
        }

        override fun remove(body: BodyAccess) {
            body as JtBodyAccess
            handle.bodyInterface.removeBody(body.id.id)
        }
    }

    override val broadQuery = object : PhysicsSpace.BroadQuery {
        override fun overlapSphere(position: Vec3d, radius: Float): Collection<JtBodyAccess> {
            val results = ArrayList<BodyId>()
            return useArena {
                handle.broadPhaseQuery.collideSphere(
                    position.f().toJolt(), radius,
                    CollideShapeBodyCollector.of(this) { result ->
                        results += BodyId(result)
                    },
                    // TODO filters
                    BroadPhaseLayerFilter.passthrough(),
                    ObjectLayerFilter.passthrough())
                results.map { bodyAccess(it) }
            }
        }
    }

    override val narrowQuery = object : PhysicsSpace.NarrowQuery {
        fun MemorySession.RayCastSettings() = RayCastSettings.of(this).apply {
            backFaceMode = BackFaceMode.IGNORE_BACK_FACES
            treatConvexAsSolid = true
        }

        override fun rayCastBody(ray: Ray, distance: Float): PhysicsSpace.RayCast? {
            return useArena {
                val hit = RayCastResult.of(this)
                val result = handle.narrowPhaseQuery.castRay(
                    ray.toJolt(distance),
                    hit,
                    // TODO filters
                    BroadPhaseLayerFilter.passthrough(),
                    ObjectLayerFilter.passthrough(),
                    BodyFilter.passthrough(),
                )
                if (result) PhysicsSpace.RayCast(bodyAccess(BodyId(hit.bodyId))) else null
            }
        }

        override fun rayCastBodies(ray: Ray, distance: Float): Collection<BodyAccess> {
            val results = ArrayList<BodyId>()
            useArena {
                handle.narrowPhaseQuery.castRay(
                    ray.toJolt(distance),
                    RayCastSettings(),
                    CastRayCollector.of(this) { result ->
                        results += BodyId(result.bodyId)
                    },
                    // TODO filters
                    BroadPhaseLayerFilter.passthrough(),
                    ObjectLayerFilter.passthrough(),
                    BodyFilter.passthrough(),
                    ShapeFilter.passthrough()
                )
            }
            return results.map { bodyAccess(it) }
        }
    }

    override val numBodies get() = handle.numBodies
    override val numActiveBodies get() = handle.numActiveBodies

    fun destroy() {
        // TODO delete bodies
        handle.destroy()
        tempAllocator.destroy()
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
