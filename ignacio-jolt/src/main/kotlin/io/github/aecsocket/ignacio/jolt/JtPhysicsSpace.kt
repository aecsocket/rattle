package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.*
import io.github.aecsocket.ignacio.Constraint
import io.github.aecsocket.klam.*
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
import jolt.physics.constraint.*
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
            position: DVec3,
            rotation: FQuat,
            motionType: MotionType
        ): BodyCreationSettings {
            val bodySettings = BodyCreationSettings.of(mem,
                (descriptor.shape as JtShape).handle,
                mem.asJolt(position),
                mem.asJolt(rotation),
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

        override fun createStatic(descriptor: StaticBodyDescriptor, position: DVec3, rotation: FQuat): PhysicsBody {
            engine.assertThread()
            return pushArena { arena ->
                val bodySettings = createBodySettings(arena, descriptor, position, rotation, MotionType.STATIC)
                createBody(bodySettings)
            }
        }

        override fun createMoving(descriptor: MovingBodyDescriptor, position: DVec3, rotation: FQuat): PhysicsBody {
            engine.assertThread()
            return pushArena { arena ->
                val bodySettings = createBodySettings(
                    arena,
                    descriptor,
                    position,
                    rotation,
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
            if (body.isDestroyed.getAndSet(true))
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
                if (body.isDestroyed.getAndSet(true))
                    throw IllegalStateException("Body $body [$idx] is already destroyed")
            }

            handle.bodyInterface.destroyBodies(bodies.map { it.id })
        }

        override fun add(body: PhysicsBody) {
            engine.assertThread()
            body as JtPhysicsBody
            if (body.isDestroyed.get())
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
                if (body.isDestroyed.get())
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
            if (body.isDestroyed.get())
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
                if (body.isDestroyed.get())
                    throw IllegalStateException("Body $body [$idx] is destroyed")
                if (!body.isAdded)
                    throw IllegalStateException("Body $body [$idx] is not added to physics space")
            }

            handle.bodyInterface.removeBodies(bodies.map { it.id })
        }
    }

    override val constraints = object : PhysicsSpace.Constraints {
        override fun create(descriptor: ConstraintDescriptor, targetA: ConstraintTarget, targetB: ConstraintTarget) = JtConstraint(pushArena { arena ->
            val handle: TwoBodyConstraintSettings = when (descriptor) {
                is FixedJointDescriptor -> FixedConstraintSettings.of().apply {
                    space = ConstraintSpace.LOCAL_TO_BODY_COM
                    setPoint1(arena.asJolt(descriptor.pointA))
                    setAxisX1(arena.asJolt(descriptor.axisXA))
                    setAxisY1(arena.asJolt(descriptor.axisYA))
                    setPoint2(arena.asJolt(descriptor.pointB))
                    setAxisX2(arena.asJolt(descriptor.axisXB))
                    setAxisY2(arena.asJolt(descriptor.axisYB))
                }
                is PointJointDescriptor -> PointConstraintSettings.of().apply {
                    space = ConstraintSpace.LOCAL_TO_BODY_COM
                    setPoint1(arena.asJolt(descriptor.pointA))
                    setPoint2(arena.asJolt(descriptor.pointB))
                }
                is HingeJointDescriptor -> HingeConstraintSettings.of().apply {
                    space = ConstraintSpace.LOCAL_TO_BODY_COM
                    setPoint1(arena.asJolt(descriptor.pointA))
                    setHingeAxis1(arena.asJolt(descriptor.hingeAxisA))
                    setNormalAxis1(arena.asJolt(descriptor.normalAxisA))
                    setPoint2(arena.asJolt(descriptor.pointB))
                    setHingeAxis2(arena.asJolt(descriptor.hingeAxisB))
                    setHingeAxis2(arena.asJolt(descriptor.normalAxisB))
                    limitsMin = descriptor.limits.x
                    limitsMax = descriptor.limits.y
                    maxFrictionTorque = descriptor.maxFrictionTorque
                }
                is SliderJointDescriptor -> SliderConstraintSettings.of().apply {
                    space = ConstraintSpace.LOCAL_TO_BODY_COM
                    setPoint1(arena.asJolt(descriptor.pointA))
                    setSliderAxis1(arena.asJolt(descriptor.sliderAxisA))
                    setNormalAxis1(arena.asJolt(descriptor.normalAxisA))
                    setPoint2(arena.asJolt(descriptor.pointB))
                    setSliderAxis2(arena.asJolt(descriptor.sliderAxisB))
                    setNormalAxis2(arena.asJolt(descriptor.normalAxisB))
                    limitsMin = descriptor.limits.x
                    limitsMax = descriptor.limits.y
                    frequency = descriptor.frequency
                    damping = descriptor.damping
                    maxFrictionForce = descriptor.maxFrictionForce
                }
                is DistanceJointDescriptor -> DistanceConstraintSettings.of().apply {
                    space = ConstraintSpace.LOCAL_TO_BODY_COM
                    setPoint1(arena.asJolt(descriptor.pointA))
                    setPoint2(arena.asJolt(descriptor.pointB))
                    minDistance = descriptor.limits.x
                    maxDistance = descriptor.limits.y
                    frequency = descriptor.frequency
                    damping = descriptor.damping
                }
                is ConeJointDescriptor -> ConeConstraintSettings.of().apply {
                    space = ConstraintSpace.LOCAL_TO_BODY_COM
                    setPoint1(arena.asJolt(descriptor.pointA))
                    setTwistAxis1(arena.asJolt(descriptor.twistAxisA))
                    setPoint2(arena.asJolt(descriptor.pointB))
                    setTwistAxis2(arena.asJolt(descriptor.twistAxisB))
                    halfConeAngle = descriptor.halfConeAngle
                }
                is SwingTwistJointDescriptor -> SwingTwistConstraintSettings.of().apply {
                    space = ConstraintSpace.LOCAL_TO_BODY_COM
                    setPoint1(arena.asJolt(descriptor.pointA))
                    setTwistAxis1(arena.asJolt(descriptor.twistAxisA))
                    setPlaneAxis1(arena.asJolt(descriptor.planeAxisA))
                    setPoint2(arena.asJolt(descriptor.pointB))
                    setTwistAxis2(arena.asJolt(descriptor.twistAxisB))
                    setPlaneAxis2(arena.asJolt(descriptor.planeAxisB))
                    normalHalfConeAngle = descriptor.normalHalfConeAngle
                    planeHalfConeAngle = descriptor.planeHalfConeAngle
                    twistMinAngle = descriptor.twistLimits.x
                    twistMaxAngle = descriptor.twistLimits.y
                    maxFrictionTorque = descriptor.maxFrictionTorque
                }
                is SixDOFJointDescriptor -> SixDOFConstraintSettings.of().apply {
                    setPosition1(arena.asJolt(descriptor.pointA))
                    setAxisX1(arena.asJolt(descriptor.axisXA))
                    setAxisY1(arena.asJolt(descriptor.axisYA))
                    setPosition2(arena.asJolt(descriptor.pointB))
                    setAxisX2(arena.asJolt(descriptor.axisXB))
                    setAxisY2(arena.asJolt(descriptor.axisYB))
                    // todo frictions and limits
                }
            }
            handle.create(targetA.asBody(), targetB.asBody())
        })

        override fun destroy(constraint: Constraint) {
            constraint as JtConstraint
            if (constraint.isDestroyed.get())
                throw IllegalStateException("Constraint $constraint is already destroyed")
            // TODO check the constraint is removed first

            handle.removeConstraint(constraint.handle)
        }

        override fun destroyAll(constraints: Collection<Constraint>) {
            @Suppress("UNCHECKED_CAST")
            constraints as Collection<JtConstraint>
            if (constraints.isEmpty()) return
            constraints.forEachIndexed { idx, constraint ->
                if (constraint.isDestroyed.get())
                    throw IllegalStateException("Constraint $constraint [$idx] is already destroyed")
                // TODO check the constraint is removed first
            }

            handle.removeConstraints(constraints.map { it.handle })
        }

        override fun add(constraint: Constraint) {
            constraint as JtConstraint
            if (constraint.isDestroyed.get())
                throw IllegalStateException("Constraint $constraint is destroyed")
            // TODO check the constraint is removed first

            handle.addConstraint(constraint.handle)
        }

        override fun addAll(constraints: Collection<Constraint>) {
            @Suppress("UNCHECKED_CAST")
            constraints as Collection<JtConstraint>
            if (constraints.isEmpty()) return
            constraints.forEachIndexed { idx, constraint ->
                if (constraint.isDestroyed.get())
                    throw IllegalStateException("Constraint $constraint [$idx] is destroyed")
                // TODO check the constraint is removed first
            }

            handle.addConstraints(constraints.map { it.handle })
        }

        override fun remove(constraint: Constraint) {
            constraint as JtConstraint
            if (constraint.isDestroyed.get())
                throw IllegalStateException("Constraint $constraint is destroyed")
            // TODO check the constraint is removed first

            handle.removeConstraint(constraint.handle)
        }

        override fun removeAll(constraints: Collection<Constraint>) {
            @Suppress("UNCHECKED_CAST")
            constraints as Collection<JtConstraint>
            if (constraints.isEmpty()) return
            constraints.forEachIndexed { idx, constraint ->
                if (constraint.isDestroyed.get())
                    throw IllegalStateException("Constraint $constraint [$idx] is destroyed")
                // TODO check the constraint is removed first
            }

            handle.removeConstraints(constraints.map { it.handle })
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
