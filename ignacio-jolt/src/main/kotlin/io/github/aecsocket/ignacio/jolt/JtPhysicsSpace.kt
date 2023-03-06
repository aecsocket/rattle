package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.BodyRef
import io.github.aecsocket.ignacio.core.ContactListener
import io.github.aecsocket.ignacio.core.math.Ray
import io.github.aecsocket.ignacio.core.math.Transform
import io.github.aecsocket.ignacio.core.math.Vec3d
import io.github.aecsocket.ignacio.core.math.f
import jolt.core.TempAllocator
import jolt.math.DVec3
import jolt.math.FMat44
import jolt.physics.Activation
import jolt.physics.PhysicsStepListener
import jolt.physics.PhysicsSystem
import jolt.physics.body.Body
import jolt.physics.body.BodyCreationSettings
import jolt.physics.body.MassProperties
import jolt.physics.body.MotionType
import jolt.physics.body.MutableBody
import jolt.physics.body.OverrideMassProperties
import jolt.physics.collision.*
import jolt.physics.collision.broadphase.BroadPhaseLayerFilter
import jolt.physics.collision.broadphase.CollideShapeBodyCollector
import jolt.physics.collision.shape.SubShapeIdPair
import java.lang.foreign.MemorySession

class JtPhysicsSpace(
    private val engine: JoltEngine,
    val handle: PhysicsSystem,
    val tempAllocator: TempAllocator,
    settings: PhysicsSpace.Settings,
) : PhysicsSpace {
    private val destroyed = DestroyFlag()
    private val arena = MemorySession.openConfined()
    
    private fun updateSettings() {
        useMemory {
            handle.setGravity(settings.gravity.toJolt())
        }
    }

    override var settings = settings
        set(value) {
            field = value
            updateSettings()
        }

    fun bodyRef(id: JBodyId) = JtBodyRef(handle, id)

    fun readAccess(body: Body) = JtBodyRef(handle, JBodyId(body.id)).readAccess(body)

    fun writeAccess(body: MutableBody) = JtBodyRef(handle, JBodyId(body.id)).writeAccess(body)

    override val bodies = object : PhysicsSpace.Bodies {
        override val num get() = handle.numBodies
        override val numActive get() = handle.numActiveBodies

        context(MemorySession)
        private fun createBodySettings(settings: BodySettings, transform: Transform, motionType: MotionType): BodyCreationSettings {
            return BodyCreationSettings.of(this@MemorySession,
                (settings.geometry as JtGeometry).handle,
                transform.position.toJolt(),
                transform.rotation.toJolt(),
                motionType,
                (settings.layer as JtObjectLayer).layer.id,
            ).also { bodySettings ->
                bodySettings.isSensor = settings.isSensor
            }
        }

        override fun createStatic(settings: StaticBodySettings, transform: Transform): BodyRef.StaticWrite {
            return useMemory {
                val bodySettings = createBodySettings(settings, transform, MotionType.STATIC)
                val body = handle.bodyInterface.createBody(bodySettings)
                writeAccess(body) as BodyRef.StaticWrite
            }
        }

        override fun createMoving(settings: MovingBodySettings, transform: Transform): BodyRef.MovingWrite {
            return useMemory {
                val bodySettings = createBodySettings(settings, transform, MotionType.DYNAMIC).also { bodySettings ->
                    bodySettings.isSensor = settings.isSensor
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
                }
                val body = handle.bodyInterface.createBody(bodySettings)
                writeAccess(body) as BodyRef.MovingWrite
            }
        }

        override fun destroy(bodyRef: BodyRef) {
            bodyRef as JtBodyRef
            if (handle.bodyInterface.isAdded(bodyRef.id.id))
                throw IllegalStateException("Body is still added to physics space")
            handle.bodyInterface.destroyBody(bodyRef.id.id)
        }

        override fun destroyAll(bodyRefs: Collection<BodyRef>) {
            bodyRefs.forEach { body ->
                body as JtBodyRef
                handle.bodyInterface.destroyBody(body.id.id)
            }
        }

        override fun add(bodyRef: BodyRef, activate: Boolean) {
            bodyRef as JtBodyRef
            handle.bodyInterface.addBody(bodyRef.id.id, Activation.ofValue(activate))
        }

        fun Iterable<JtBodyRef>.ids() = map { it.id.id }

        override fun addAll(bodyRefs: Collection<BodyRef>, activate: Boolean) {
            @Suppress("UNCHECKED_CAST")
            bodyRefs as Collection<JtBodyRef>
            val bulk = handle.bodyInterface.bodyBulk(bodyRefs.ids())
            handle.bodyInterface.addBodiesPrepare(bulk)
            handle.bodyInterface.addBodiesFinalize(bulk, Activation.ofValue(activate))
        }

        override fun remove(bodyRef: BodyRef) {
            bodyRef as JtBodyRef
            handle.bodyInterface.removeBody(bodyRef.id.id)
        }

        override fun removeAll(bodyRefs: Collection<BodyRef>) {
            @Suppress("UNCHECKED_CAST")
            bodyRefs as Collection<JtBodyRef>
            handle.bodyInterface.removeBodies(bodyRefs.ids())
        }

        override fun addStatic(settings: StaticBodySettings, transform: Transform): BodyRef.StaticWrite {
            val body = createStatic(settings, transform)
            add(body.ref, false)
            return body
        }

        override fun addMoving(
            settings: MovingBodySettings,
            transform: Transform,
            activate: Boolean
        ): BodyRef.MovingWrite {
            val body = createMoving(settings, transform)
            add(body.ref, activate)
            return body
        }

        override fun all(): Collection<BodyRef> {
            return handle.bodies.map { bodyRef(JBodyId(it)) }
        }

        override fun active(): Collection<BodyRef> {
            return handle.activeBodies.map { bodyRef(JBodyId(it)) }
        }
    }

    override val broadQuery = object : PhysicsSpace.BroadQuery {
        override fun overlapSphere(position: Vec3d, radius: Float): Collection<JtBodyRef> {
            val results = ArrayList<JBodyId>()
            return useMemory {
                handle.broadPhaseQuery.collideSphere(
                    position.f().toJolt(), radius,
                    CollideShapeBodyCollector.of(this) { result ->
                        results += JBodyId(result)
                    },
                    // TODO filters
                    BroadPhaseLayerFilter.passthrough(),
                    ObjectLayerFilter.passthrough())
                results.map { bodyRef(it) }
            }
        }
    }

    override val narrowQuery = object : PhysicsSpace.NarrowQuery {
        fun MemorySession.RayCastSettings() = RayCastSettings.of(this).apply {
            backFaceMode = BackFaceMode.IGNORE_BACK_FACES
            treatConvexAsSolid = true
        }

        override fun rayCastBody(ray: Ray, distance: Float): PhysicsSpace.RayCast? {
            return useMemory {
                val hit = RayCastResult.of(this)
                val result = handle.narrowPhaseQuery.castRay(
                    ray.toJolt(distance),
                    hit,
                    // TODO filters
                    BroadPhaseLayerFilter.passthrough(),
                    ObjectLayerFilter.passthrough(),
                    BodyFilter.passthrough(),
                )
                if (result) PhysicsSpace.RayCast(bodyRef(JBodyId(hit.bodyId))) else null
            }
        }

        override fun rayCastBodies(ray: Ray, distance: Float): Collection<BodyRef> {
            val results = ArrayList<JBodyId>()
            useMemory {
                handle.narrowPhaseQuery.castRay(
                    ray.toJolt(distance),
                    RayCastSettings(),
                    CastRayCollector.of(this) { result ->
                        results += JBodyId(result.bodyId)
                    },
                    // TODO filters
                    BroadPhaseLayerFilter.passthrough(),
                    ObjectLayerFilter.passthrough(),
                    BodyFilter.passthrough(),
                    ShapeFilter.passthrough()
                )
            }
            return results.map { bodyRef(it) }
        }
    }

    private data class StepListenerData(
        val native: PhysicsStepListener,
        val arena: MemorySession,
    )

    private val stepListeners = HashMap<StepListener, StepListenerData>()
    private val contactListeners = ArrayList<ContactListener>()

    init {
        updateSettings()
        handle.contactListener = JContactListener.of(arena, object : JContactListenerFn {
            override fun onContactValidate(
                body1: Body,
                body2: Body,
                baseOffset: DVec3,
                collisionResult: CollideShapeResult
            ): ValidateResult {
                return ValidateResult.ACCEPT_ALL_CONTACTS_FOR_THIS_BODY_PAIR
            }

            override fun onContactAdded(body1: Body, body2: Body, manifold: ContactManifold, settings: ContactSettings) {
                val access1 = readAccess(body1)
                val access2 = readAccess(body2)
                contactListeners.forEach {
                    it.onAdded(access1, access2)
                }
            }

            override fun onContactPersisted(
                body1: Body,
                body2: Body,
                manifold: ContactManifold,
                settings: ContactSettings
            ) {}

            override fun onContactRemoved(subShapeIdPair: SubShapeIdPair) {
                val access1 = bodyRef(JBodyId(subShapeIdPair.bodyId1))
                val access2 = bodyRef(JBodyId(subShapeIdPair.bodyId2))
                contactListeners.forEach {
                    it.onRemoved(access1, access2)
                }
            }
        })
    }

    override fun destroy() {
        destroyed.mark()
        stepListeners.forEach { (_, listener) ->
            listener.arena.close()
        }
        engine.spaces.remove(handle)
        // TODO delete bodies
        handle.destroy()
        tempAllocator.destroy()
        arena.close()
    }

    override fun onStep(listener: StepListener) {
        val arena = MemorySession.openConfined()
        val native = PhysicsStepListener.of(arena) { deltaTime, _ ->
            listener.onStep(deltaTime)
        }
        handle.addStepListener(native)
        stepListeners[listener] = StepListenerData(native, arena)
    }

    override fun removeStepListener(listener: StepListener) {
        val data = stepListeners.remove(listener) ?: return
        handle.removeStepListener(data.native)
        data.arena.close()
    }

    override fun onContact(listener: ContactListener) {
        contactListeners += listener
    }

    override fun removeContactListener(listener: ContactListener) {
        contactListeners -= listener
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
