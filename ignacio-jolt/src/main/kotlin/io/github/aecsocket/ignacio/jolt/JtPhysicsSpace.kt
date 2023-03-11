package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.PhysicsBody
import io.github.aecsocket.ignacio.core.ContactListener
import io.github.aecsocket.ignacio.core.ContactManifold
import io.github.aecsocket.ignacio.core.math.*
import jolt.core.TempAllocator
import jolt.math.DVec3
import jolt.math.FMat44
import jolt.physics.Activation
import jolt.physics.PhysicsStepListener
import jolt.physics.PhysicsSystem
import jolt.physics.body.Body
import jolt.physics.body.BodyCreationSettings
import jolt.physics.body.BodyLockRead
import jolt.physics.body.MassProperties
import jolt.physics.body.MotionType
import jolt.physics.body.MutableBody
import jolt.physics.body.OverrideMassProperties
import jolt.physics.collision.*
import jolt.physics.collision.broadphase.BroadPhaseLayerFilter
import jolt.physics.collision.broadphase.CollideShapeBodyCollector
import jolt.physics.collision.shape.SubShapeIdPair
import java.lang.Exception
import java.lang.foreign.MemorySession
import java.util.concurrent.ConcurrentHashMap

class JtPhysicsSpace(
    private val engine: JoltEngine,
    val handle: PhysicsSystem,
    private val tempAllocator: TempAllocator,
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

    private val bodyWrappers = ConcurrentHashMap<BodyId, JtPhysicsBody>()
    // cache numBodies and numActiveBodies after each update
    // otherwise, accessing them directly from the handle is *really slow* when many bodies are active
    // (it uses the body mutex lock)
    private var numBodies = 0
    private var numActiveBodies = 0

    fun bodyOf(id: BodyId, added: Boolean = true, name: String? = null) = bodyWrappers.computeIfAbsent(id) {
        JtPhysicsBody(handle, id, name, added)
    }

    fun readAccess(body: Body) = bodyOf(BodyId(body.id)).readAccess(body)

    fun writeAccess(body: MutableBody) = bodyOf(BodyId(body.id)).writeAccess(body)

    override val bodies = object : PhysicsSpace.Bodies {
        override val num get() = numBodies
        override val numActive get() = numActiveBodies

        context(MemorySession)
        private fun createBodySettings(settings: BodySettings, transform: Transform, motionType: MotionType): BodyCreationSettings {
            return BodyCreationSettings.of(this@MemorySession,
                (settings.shape as JtShape).handle,
                transform.position.toJolt(),
                transform.rotation.toJolt(),
                motionType,
                (settings.layer as JtObjectLayer).layer.id,
            ).also { bodySettings ->
                bodySettings.isSensor = settings.isSensor
            }
        }

        private fun createBody(bodySettings: BodyCreationSettings, settings: BodySettings): PhysicsBody.Write {
            val handle = handle.bodyInterface.createBody(bodySettings)
            val body = bodyOf(BodyId(handle.id), name = settings.name, added = false)
            return body.writeAccess(handle)
        }

        override fun createStatic(settings: StaticBodySettings, transform: Transform): PhysicsBody.StaticWrite {
            return useMemory {
                val bodySettings = createBodySettings(settings, transform, MotionType.STATIC)
                createBody(bodySettings, settings) as PhysicsBody.StaticWrite
            }
        }

        override fun createMoving(settings: MovingBodySettings, transform: Transform): PhysicsBody.MovingWrite {
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
                createBody(bodySettings, settings) as PhysicsBody.MovingWrite
            }
        }

        override fun create(settings: BodySettings, transform: Transform): PhysicsBody.Write {
            return when (settings) {
                is StaticBodySettings -> createStatic(settings, transform)
                is MovingBodySettings -> createMoving(settings, transform)
            }
        }

        override fun destroy(body: PhysicsBody) {
            body as JtPhysicsBody
            try {
                body.assertCanBeDestroyed()
            } catch (ex: Exception) {
                throw IllegalStateException("Could not destroy body $body: ${ex.message}")
            }
            body.isDestroyed = true
            handle.bodyInterface.destroyBody(body.id.id)
        }

        override fun destroyAll(bodies: Collection<PhysicsBody>) {
            @Suppress("UNCHECKED_CAST")
            bodies as Collection<JtPhysicsBody>
            if (bodies.isEmpty()) return
            bodies.forEachIndexed { idx, body ->
                try {
                    body.assertCanBeDestroyed()
                } catch (ex: Exception) {
                    throw IllegalStateException("Could not destroy body $body (index $idx): ${ex.message}")
                }
                body.isDestroyed = true
                bodyWrappers -= body.id
                handle.bodyInterface.destroyBody(body.id.id)
            }
        }

        override fun add(body: PhysicsBody, activate: Boolean) {
            body as JtPhysicsBody
            try {
                body.assertCanBeAdded()
            } catch (ex: Exception) {
                throw IllegalStateException("Could not add body $body: ${ex.message}")
            }
            body.added = true
            handle.bodyInterface.addBody(body.id.id, Activation.ofValue(activate))
        }

        fun Iterable<JtPhysicsBody>.ids() = map { it.id.id }

        override fun addAll(bodies: Collection<PhysicsBody>, activate: Boolean) {
            if (bodies.isEmpty()) return
            @Suppress("UNCHECKED_CAST")
            bodies as Collection<JtPhysicsBody>
            bodies.forEachIndexed { idx, body ->
                try {
                    body.assertCanBeAdded()
                } catch (ex: Exception) {
                    throw IllegalStateException("Could not add body $body (index $idx): ${ex.message}")
                }
                body.added = true
            }
            val bulk = handle.bodyInterface.bodyBulk(bodies.ids())
            handle.bodyInterface.addBodiesPrepare(bulk)
            handle.bodyInterface.addBodiesFinalize(bulk, Activation.ofValue(activate))
        }

        override fun remove(body: PhysicsBody) {
            body as JtPhysicsBody
            try {
                body.assertCanBeRemoved()
            } catch (ex: Exception) {
                throw IllegalStateException("Could not remove body $body: ${ex.message}")
            }
            body.added = false
            handle.bodyInterface.removeBody(body.id.id)
        }

        override fun removeAll(bodies: Collection<PhysicsBody>) {
            if (bodies.isEmpty()) return
            @Suppress("UNCHECKED_CAST")
            bodies as Collection<JtPhysicsBody>
            bodies.forEachIndexed { idx, body ->
                try {
                    body.assertCanBeRemoved()
                } catch (ex: Exception) {
                    throw IllegalStateException("Could not remove body $body (index $idx): ${ex.message}")
                }
                body.added = false
            }
            handle.bodyInterface.removeBodies(bodies.ids())
        }

        override fun addStatic(settings: StaticBodySettings, transform: Transform): PhysicsBody.StaticWrite {
            val body = createStatic(settings, transform)
            add(body.body, false)
            return body
        }

        override fun addMoving(
            settings: MovingBodySettings,
            transform: Transform,
            activate: Boolean
        ): PhysicsBody.MovingWrite {
            val body = createMoving(settings, transform)
            add(body.body, activate)
            return body
        }

        override fun all(): Collection<PhysicsBody> {
            return handle.bodies.map { bodyOf(BodyId(it)) }
        }

        override fun active(): Collection<PhysicsBody> {
            return handle.activeBodies.map { bodyOf(BodyId(it)) }
        }
    }

    override val broadQuery = object : PhysicsSpace.BroadQuery {
        override fun overlapSphere(position: Vec3d, radius: Float, filter: BroadFilter?): Collection<JtPhysicsBody> {
            val filterHandle = filter as? JtBroadFilter
            if (filterHandle?.destroyed() == true)
                throw IllegalArgumentException("Filter is destroyed")
            val results = ArrayList<BodyId>()
            return useMemory {
                handle.broadPhaseQuery.collideSphere(
                    position.f().toJolt(), radius,
                    CollideShapeBodyCollector.of(this) { result ->
                        results += BodyId(result)
                    },
                    filterHandle?.broad ?: BroadPhaseLayerFilter.passthrough(),
                    filterHandle?.objects ?: ObjectLayerFilter.passthrough(),
                )
                results.map { bodyOf(it) }
            }
        }

        override fun overlapAABox(box: AABB, filter: BroadFilter?): Collection<PhysicsBody> {
            val filterHandle = filter as? JtBroadFilter
            if (filterHandle?.destroyed() == true)
                throw IllegalArgumentException("Filter is destroyed")
            val results = ArrayList<BodyId>()
            return useMemory {
                handle.broadPhaseQuery.collideAABox(
                    box.toJoltF(),
                    CollideShapeBodyCollector.of(this) { result ->
                        results += BodyId(result)
                    },
                    filterHandle?.broad ?: BroadPhaseLayerFilter.passthrough(),
                    filterHandle?.objects ?: ObjectLayerFilter.passthrough(),
                )
                results.map { bodyOf(it) }
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

                if (result) {
                    val bodyId = BodyId(hit.bodyId)
                    val inDistance = hit.fraction * distance
                    val inPosition = ray.at(inDistance)
                    var normal: Vec3f? = null
                    useMemory {
                        bodyId.lockRead(handle.bodyLockInterface) { bodyRead ->
                            val out = FVec3()
                            bodyRead.getWorldSpaceSurfaceNormal(hit.subShapeId, inPosition.toJolt(), out)
                            normal = out.toIgnacio()
                        }
                    }

                    normal?.let { normal2 -> PhysicsSpace.RayCast(
                        body = bodyOf(bodyId),
                        inFraction = hit.fraction,
                        inDistance = inDistance,
                        inPosition = inPosition,
                        normal = normal2,
                    ) }
                } else null
            }
        }

        override fun rayCastBodies(ray: Ray, distance: Float): Collection<PhysicsBody> {
            val results = ArrayList<BodyId>()
            useMemory {
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
            return results.map { bodyOf(it) }
        }
    }

    private val preStepListeners = HashSet<PreStepListener>()
    private val stepListeners = HashSet<StepListener>()
    private val contactListeners = HashSet<ContactListener>()

    init {
        updateSettings()
        handle.addStepListener(PhysicsStepListener.of(arena) { deltaTime, _ ->
            stepListeners.forEach {
                it.onStep(deltaTime)
            }
        })
        handle.contactListener = JContactListener.of(arena, object : JContactListenerFn {
            override fun onContactValidate(
                body1: Body,
                body2: Body,
                baseOffset: DVec3,
                collisionResult: CollideShapeResult
            ): ValidateResult {
                return ValidateResult.ACCEPT_ALL_CONTACTS_FOR_THIS_BODY_PAIR
            }

            override fun onContactAdded(body1: Body, body2: Body, manifold: JContactManifold, settings: ContactSettings) {
                val access1 = readAccess(body1)
                val access2 = readAccess(body2)
                val igManifold = ContactManifold(
                    position = manifold.baseOffsetD.toIgnacio(),
                    penetrationDepth = manifold.penetrationDepth,
                    normal = manifold.worldSpaceNormal.toIgnacio(),
                )
                contactListeners.forEach {
                    it.onAdded(access1, access2, igManifold)
                }
            }

            override fun onContactPersisted(
                body1: Body,
                body2: Body,
                manifold: JContactManifold,
                settings: ContactSettings
            ) {}

            override fun onContactRemoved(subShapeIdPair: SubShapeIdPair) {
                val access1 = bodyOf(BodyId(subShapeIdPair.bodyId1))
                val access2 = bodyOf(BodyId(subShapeIdPair.bodyId2))
                contactListeners.forEach {
                    it.onRemoved(access1, access2)
                }
            }
        })
    }

    override fun destroy() {
        destroyed.mark()
        engine.spaces.remove(handle)
        // TODO delete bodies
        handle.destroy()
        tempAllocator.destroy()
        arena.close()
    }

    override fun onPreStep(listener: PreStepListener) {
        preStepListeners += listener
    }

    override fun removePreStepListener(listener: PreStepListener) {
        preStepListeners -= listener
    }

    override fun onStep(listener: StepListener) {
        stepListeners += listener
    }

    override fun removeStepListener(listener: StepListener) {
        stepListeners -= listener
    }

    override fun onContact(listener: ContactListener) {
        contactListeners += listener
    }

    override fun removeContactListener(listener: ContactListener) {
        contactListeners -= listener
    }

    override fun update(deltaTime: Float) {
        preStepListeners.forEach {
            it.onPreStep(deltaTime)
        }
        handle.update(
            deltaTime,
            engine.settings.spaces.collisionSteps,
            engine.settings.spaces.integrationSubSteps,
            tempAllocator,
            engine.jobSystem
        )
        numBodies = handle.numBodies
        numActiveBodies = handle.numActiveBodies
    }
}
