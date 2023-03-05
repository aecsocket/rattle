package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.BodyAccess
import io.github.aecsocket.ignacio.core.math.Ray
import io.github.aecsocket.ignacio.core.math.Transform
import io.github.aecsocket.ignacio.core.math.Vec3d
import io.github.aecsocket.ignacio.core.math.f
import jolt.core.TempAllocator
import jolt.kotlin.*
import jolt.math.FMat44
import jolt.physics.Activation
import jolt.physics.PhysicsSystem
import jolt.physics.body.MassProperties
import jolt.physics.body.MotionType
import jolt.physics.body.OverrideMassProperties
import jolt.physics.collision.*
import jolt.physics.collision.broadphase.BroadPhaseLayerFilter
import jolt.physics.collision.broadphase.CollideShapeBodyCollector
import java.lang.foreign.MemorySession

class JtPhysicsSpace(
    private val engine: JoltEngine,
    val handle: PhysicsSystem,
    val tempAllocator: TempAllocator,
    settings: PhysicsSpace.Settings,
) : PhysicsSpace {
    private val destroy = DestroyFlag()

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
            return useArena {
                val bodySettings = BodyCreationSettings(this,
                    (settings.geometry as JtGeometry).handle,
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
            return useArena {
                val bodySettings = BodyCreationSettings(this,
                    (settings.geometry as JtGeometry).handle,
                    transform.position.toJolt(),
                    transform.rotation.toJolt(),
                    MotionType.DYNAMIC,
                    objectLayerMoving
                )
                bodySettings.overrideMassProperties = OverrideMassProperties.CALCULATE_INERTIA
                bodySettings.massPropertiesOverride = MassProperties.of(this, settings.mass, FMat44.of(this, 0f))
                bodySettings.linearVelocity = settings.linearVelocity.toJolt()
                bodySettings.angularVelocity = settings.angularVelocity.toJolt()
                bodySettings.friction = settings.friction
                bodySettings.restitution = settings.restitution
                bodySettings.linearDamping = settings.linearDamping
                bodySettings.angularDamping = settings.angularDamping
                bodySettings.maxLinearVelocity = settings.maxLinearVelocity
                bodySettings.maxAngularVelocity = settings.maxAngularVelocity
                bodySettings.gravityFactor = settings.gravityFactor
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

    override fun destroy() {
        destroy.mark()
        engine.spaces.remove(handle)
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
