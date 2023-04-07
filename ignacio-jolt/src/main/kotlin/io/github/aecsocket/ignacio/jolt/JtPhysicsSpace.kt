package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.*
import io.github.aecsocket.klam.DAabb3
import io.github.aecsocket.klam.DRay3
import io.github.aecsocket.klam.DVec3
import io.github.aecsocket.klam.FVec3
import jolt.core.TempAllocator
import jolt.physics.Activation
import jolt.physics.PhysicsStepListener
import jolt.physics.PhysicsSystem
import jolt.physics.body.BodyCreationSettings
import jolt.physics.body.MassProperties
import jolt.physics.body.MotionType
import jolt.physics.body.OverrideMassProperties
import jolt.physics.collision.CastRayCollector
import jolt.physics.collision.RayCastResult
import jolt.physics.collision.RayCastSettings
import jolt.physics.collision.broadphase.CollideShapeBodyCollector
import jolt.physics.collision.broadphase.RayCastBodyCollector
import java.lang.foreign.MemorySession
import java.util.concurrent.atomic.AtomicInteger

class JtPhysicsSpace internal constructor(
    val engine: JoltEngine,
    val handle: PhysicsSystem,
    val tempAllocator: TempAllocator,
    settings: PhysicsSpace.Settings,
) : PhysicsSpace {
    private val destroyed = DestroyFlag()
    private val arena = MemorySession.openShared()

    private fun updateSettings() {
        pushArena { arena ->
            handle.setGravity(arena.asJolt(settings.gravity))
        }
    }

    override var settings = settings
        set(value) {
            field = value
            updateSettings()
        }

    private val onStep = ArrayList<StepListener>()
    // cache number of bodies after each update
    // this isn't immediately updated, but accessing them directly from Jolt is *really slow* when many bodies are active
    // since it locks the entire body mutex, and causes the calling thread (potentially main!) to block
    private val numBodies = AtomicInteger()
    private val numActiveBodies = AtomicInteger()

    init {
        updateSettings()
        handle.addStepListener(PhysicsStepListener.of(arena) { deltaTime, _ ->
            onStep.forEach { it.onStep(deltaTime) }
        })
    }

    override fun destroy() {
        destroyed.mark()
        handle.delete()
        arena.close()
    }

    fun bodyOf(id: Int) = JtPhysicsBody(engine, handle, id)

    override val bodies = object : PhysicsSpace.Bodies {
        override val count get() = numBodies.get()

        override val activeCount get() = numActiveBodies.get()

        override fun all() = engine.withThreadAssert {
            handle.bodies.map { bodyOf(it) }
        }

        override fun active() = engine.withThreadAssert {
            handle.activeBodies.map { bodyOf(it) }
        }

        private fun createBodySettings(
            mem: MemorySession,
            descriptor: BodyDescriptor,
            transform: Transform,
            motionType: MotionType
        ): BodyCreationSettings {
            val bodySettings = BodyCreationSettings.of(mem,
                (descriptor.shape as JtShape).handle,
                mem.asJolt(transform.position),
                mem.asJolt(transform.rotation),
                motionType,
                (descriptor.contactFilter as JoltEngine.JtBodyContactFilter).id,
            )
            bodySettings.isSensor = descriptor.isTrigger
            bodySettings.friction = descriptor.friction
            bodySettings.restitution = descriptor.restitution
            return bodySettings
        }

        private fun createBody(settings: BodyCreationSettings): JtPhysicsBody {
            val body = handle.bodyInterface.createBody(settings)
            return bodyOf(body.id)
        }

        override fun createStatic(descriptor: StaticBodyDescriptor, transform: Transform): PhysicsBody {
            engine.assertThread()
            return pushArena { arena ->
                val bodySettings = createBodySettings(arena, descriptor, transform, MotionType.STATIC)
                createBody(bodySettings)
            }
        }

        override fun createMoving(descriptor: MovingBodyDescriptor, transform: Transform): PhysicsBody {
            engine.assertThread()
            return pushArena { arena ->
                val bodySettings = createBodySettings(
                    arena,
                    descriptor,
                    transform,
                    if (descriptor.isKinematic) MotionType.KINEMATIC else MotionType.DYNAMIC,
                )
                when (val mass = descriptor.mass) {
                    is Mass.WithInertia -> {
                        bodySettings.overrideMassProperties = OverrideMassProperties.MASS_AND_INERTIA_PROVIDED
                        bodySettings.massPropertiesOverride = MassProperties.of(arena,
                            mass.mass,
                            arena.asJolt(mass.inertia)
                        )
                    }
                    is Mass.Constant -> {
                        bodySettings.overrideMassProperties = OverrideMassProperties.CALCULATE_INERTIA
                        bodySettings.massPropertiesOverride = MassProperties.of(arena,
                            mass.mass,
                            arena.FMat44(),
                        )
                    }
                    is Mass.Calculate -> {
                        bodySettings.overrideMassProperties = OverrideMassProperties.CALCULATE_MASS_AND_INERTIA
                    }
                }
                bodySettings.linearVelocity = arena.asJolt(descriptor.linearVelocity)
                bodySettings.angularVelocity = arena.asJolt(descriptor.angularVelocity)
                bodySettings.gravityFactor = descriptor.gravityFactor
                bodySettings.allowSleeping = descriptor.canDeactivate
                bodySettings.linearDamping = descriptor.linearDamping
                bodySettings.angularDamping = descriptor.angularDamping
                bodySettings.maxLinearVelocity = descriptor.maxLinearVelocity
                bodySettings.maxAngularVelocity = descriptor.maxAngularVelocity
                createBody(bodySettings)
            }
        }

        override fun destroy(body: PhysicsBody) {
            engine.assertThread()
            body as JtPhysicsBody
            if (body.isAdded)
                throw IllegalStateException("Body $body is still added to physics space")
            if (body.destroyed.getAndSet(true))
                throw IllegalStateException("Body $body is already destroyed")

            handle.bodyInterface.destroyBody(body.id)
        }

        override fun destroyAll(bodies: Collection<PhysicsBody>) {
            engine.assertThread()
            @Suppress("UNCHECKED_CAST")
            bodies as Collection<JtPhysicsBody>
            if (bodies.isEmpty()) return
            bodies.forEachIndexed { idx, body ->
                if (body.isAdded)
                    throw IllegalStateException("Body $body [$idx] is still added to physics space")
                if (body.destroyed.getAndSet(true))
                    throw IllegalStateException("Body $body [$idx] is already destroyed")
            }

            handle.bodyInterface.destroyBodies(bodies.map { it.id })
        }

        override fun add(body: PhysicsBody) {
            engine.assertThread()
            body as JtPhysicsBody
            if (body.destroyed.get())
                throw IllegalStateException("Body $body is destroyed")
            if (body.isAdded)
                throw IllegalStateException("Body $body is already added to physics space")

            handle.bodyInterface.addBody(body.id, Activation.DONT_ACTIVATE)
        }

        override fun addAll(bodies: Collection<PhysicsBody>) {
            engine.assertThread()
            @Suppress("UNCHECKED_CAST")
            bodies as Collection<JtPhysicsBody>
            if (bodies.isEmpty()) return
            bodies.forEachIndexed { idx, body ->
                if (body.destroyed.get())
                    throw IllegalStateException("Body $body [$idx] is destroyed")
                if (body.isAdded)
                    throw IllegalStateException("Body $body [$idx] is already added to physics space")
            }

            val bulk = handle.bodyInterface.bodyBulk(bodies.map { it.id })
            handle.bodyInterface.addBodiesPrepare(bulk)
            handle.bodyInterface.addBodiesFinalize(bulk, Activation.DONT_ACTIVATE)
        }

        override fun remove(body: PhysicsBody) {
            engine.assertThread()
            body as JtPhysicsBody
            if (body.destroyed.get())
                throw IllegalStateException("Body $body is destroyed")
            if (!body.isAdded)
                throw IllegalStateException("Body $body is not added to physics space")

            handle.bodyInterface.removeBody(body.id)
        }

        override fun removeAll(bodies: Collection<PhysicsBody>) {
            engine.assertThread()
            @Suppress("UNCHECKED_CAST")
            bodies as Collection<JtPhysicsBody>
            if (bodies.isEmpty()) return
            bodies.forEachIndexed { idx, body ->
                if (body.destroyed.get())
                    throw IllegalStateException("Body $body [$idx] is destroyed")
                if (!body.isAdded)
                    throw IllegalStateException("Body $body [$idx] is not added to physics space")
            }

            handle.bodyInterface.removeBodies(bodies.map { it.id })
        }
    }

    override val broadQuery = object : PhysicsSpace.BroadQuery {
        override fun rayCastBodies(ray: DRay3, distance: Float, layerFilter: LayerFilter): Collection<PhysicsSpace.RayCast> {
            engine.assertThread()
            layerFilter as JtLayerFilter
            val result = ArrayList<PhysicsSpace.RayCast>()
            pushArena { arena ->
                handle.broadPhaseQuery.castRay(
                    arena.asJoltF(ray),
                    RayCastBodyCollector.of(arena) { hit ->
                        result += PhysicsSpace.RayCast(
                            body = bodyOf(hit.bodyId),
                            hitFraction = hit.fraction,
                        )
                    },
                    layerFilter.broad,
                    layerFilter.objects,
                )
            }
            return result
        }

        override fun rayCastBody(ray: DRay3, distance: Float, layerFilter: LayerFilter): PhysicsSpace.RayCast? {
            engine.assertThread()
            return rayCastBodies(ray, distance, layerFilter).firstOrNull()
        }

        override fun contactBox(box: DAabb3, layerFilter: LayerFilter): Collection<PhysicsBody> {
            engine.assertThread()
            layerFilter as JtLayerFilter
            val result = ArrayList<PhysicsBody>()
            pushArena { arena ->
                handle.broadPhaseQuery.collideAABox(
                    arena.asJoltF(box),
                    CollideShapeBodyCollector.of(arena) { hit ->
                        result += bodyOf(hit)
                    },
                    layerFilter.broad,
                    layerFilter.objects,
                )
            }
            return result
        }

        override fun contactSphere(
            position: DVec3,
            radius: Float,
            layerFilter: LayerFilter
        ): Collection<PhysicsBody> {
            engine.assertThread()
            layerFilter as JtLayerFilter
            val result = ArrayList<PhysicsBody>()
            pushArena { arena ->
                handle.broadPhaseQuery.collideSphere(
                    arena.asJolt(FVec3(position)),
                    radius,
                    CollideShapeBodyCollector.of(arena) { hit ->
                        result += bodyOf(hit)
                    },
                    layerFilter.broad,
                    layerFilter.objects,
                )
            }
            return result
        }
    }

    override val narrowQuery = object : PhysicsSpace.NarrowQuery {
        override fun rayCastBody(ray: DRay3, distance: Float, layerFilter: LayerFilter, bodyFilter: BodyFilter): PhysicsSpace.RayCast? {
            engine.assertThread()
            layerFilter as JtLayerFilter
            bodyFilter as JtBodyFilter
            return pushArena { arena ->
                val hit = RayCastResult.of(arena)
                val result = handle.narrowPhaseQuery.castRay(
                    arena.asJolt(ray),
                    hit,
                    layerFilter.broad,
                    layerFilter.objects,
                    bodyFilter.body,
                )
                if (result) PhysicsSpace.RayCast(
                    body = bodyOf(hit.bodyId),
                    hitFraction = hit.fraction,
                ) else null
            }
        }

        override fun rayCastBodies(ray: DRay3, distance: Float, layerFilter: LayerFilter, bodyFilter: BodyFilter, shapeFilter: ShapeFilter): Collection<PhysicsSpace.RayCast> {
            engine.assertThread()
            layerFilter as JtLayerFilter
            bodyFilter as JtBodyFilter
            shapeFilter as JtShapeFilter
            val result = ArrayList<PhysicsSpace.RayCast>()
            pushArena { arena ->
                val castSettings = RayCastSettings.of(arena)
                handle.narrowPhaseQuery.castRay(
                    arena.asJolt(ray),
                    castSettings,
                    CastRayCollector.of(arena) { hit ->
                        result += PhysicsSpace.RayCast(
                            body = bodyOf(hit.bodyId),
                            hitFraction = hit.fraction,
                        )
                    },
                    layerFilter.broad,
                    layerFilter.objects,
                    bodyFilter.body,
                    shapeFilter.shape,
                )
            }
            return result
        }
    }

    override fun onStep(listener: StepListener) {
        onStep += listener
    }

    override fun removeStepListener(listener: StepListener) {
        onStep -= listener
    }

    override fun update(deltaTime: Float) {
        engine.assertThread()
        handle.update(
            deltaTime,
            engine.settings.space.collisionSteps,
            engine.settings.space.integrationSubSteps,
            tempAllocator,
            engine.jobSystem,
        )
        numBodies.set(handle.numBodies)
        numActiveBodies.set(handle.numActiveBodies)
    }
}
