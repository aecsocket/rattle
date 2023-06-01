package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.RigidBody
import rapier.dynamics.*
import rapier.dynamics.joint.impulse.ImpulseJointSet
import rapier.dynamics.joint.multibody.MultibodyJointSet
import rapier.geometry.BroadPhase
import rapier.geometry.ColliderSet
import rapier.geometry.NarrowPhase
import rapier.pipeline.PhysicsPipeline
import rapier.pipeline.QueryPipeline

class RapierSpace internal constructor(
    val engine: RapierEngine,
    settings: PhysicsSpace.Settings,
) : PhysicsSpace {
    private val destroyed = DestroyFlag()

    val arena: Arena = Arena.openShared()

    val pipeline = PhysicsPipeline.create()
    val islands = IslandManager.create()
    val broadPhase = BroadPhase.create()
    val narrowPhase = NarrowPhase.create()
    val rigidBodySet = RigidBodySet.create()
    val colliderSet = ColliderSet.create()
    val impulseJointSet = ImpulseJointSet.create()
    val multibodyJointSet = MultibodyJointSet.create()
    val ccdSolver = CCDSolver.create()
    val queryPipeline = QueryPipeline.create()

    val gravity = settings.gravity.toVector(arena)

    override var settings = settings
        set(value) {
            field = value
            gravity.copyFrom(settings.gravity)
        }

    fun createIntegrationParametersDesc() = IntegrationParametersDesc.create(arena).apply {
        erp = engine.settings.integration.erp
        dampingRatio = engine.settings.integration.dampingRatio
        jointErp = engine.settings.integration.jointErp
        jointDampingRatio = engine.settings.integration.jointDampingRatio
        allowedLinearError = engine.settings.integration.allowedLinearError
        maxPenetrationCorrection = engine.settings.integration.maxPenetrationCorrection
        predictionDistance = engine.settings.integration.predictionDistance
        maxVelocityIterations = engine.settings.integration.maxVelocityIterations
        maxVelocityFrictionIterations = engine.settings.integration.maxVelocityFrictionIterations
        maxStabilizationIterations = engine.settings.integration.maxStabilizationIterations
        interleaveRestitutionAndFrictionResolution = engine.settings.integration.interleaveRestitutionAndFrictionResolution
        minIslandSize = engine.settings.integration.minIslandSize
        maxCcdSubsteps = engine.settings.integration.maxCcdSubsteps
    }

    override fun destroy() {
        destroyed()

        pipeline.drop()
        islands.drop()
        broadPhase.drop()
        narrowPhase.drop()
        rigidBodySet.drop()
        colliderSet.drop()
        impulseJointSet.drop()
        multibodyJointSet.drop()
        ccdSolver.drop()
        queryPipeline.drop()

        arena.close()
    }

    override val colliders = object : PhysicsSpace.SingleContainer<Collider> {
        override val count: Int
            get() = colliderSet.size().toInt()

        override fun all(): Collection<Collider> {
            return colliderSet.all().map {
                RapierCollider(RapierCollider.State.Added(
                    space = this@RapierSpace,
                    handle = ColliderHandle(it.handle),
                ))
            }
        }

        override fun add(value: Collider) {
            value as RapierCollider
            when (val state = value.state) {
                is RapierCollider.State.Added -> throw IllegalStateException("$value is attempting to be added to ${this@RapierSpace} but is already added to ${state.space}")
                is RapierCollider.State.Removed -> {
                    val handle = colliderSet.insert(state.coll)
                    value.state = RapierCollider.State.Added(this@RapierSpace, ColliderHandle(handle))
                }
            }
        }

        override fun remove(value: Collider) {
            value as RapierCollider
            when (val state = value.state) {
                is RapierCollider.State.Added -> {
                    if (this@RapierSpace != state.space)
                        throw IllegalStateException("$value is attempting to be removed from ${this@RapierSpace} but is added to ${state.space}")
                    println("removing coll ${state.handle} = ${state.handle.id}")
                    val coll = colliderSet.remove(
                        state.handle.id,
                        islands,
                        rigidBodySet,
                        false,
                    ) ?: throw IllegalStateException("$value does not exist in ${this@RapierSpace}")
                    println("... which has memory $coll")
                    value.state = RapierCollider.State.Removed(coll)
                }
                is RapierCollider.State.Removed -> throw IllegalStateException("$value is not added to a space")
            }
        }
    }

    override val bodies = object : PhysicsSpace.ActiveContainer<RigidBody> {
        override val count: Int
            get() = rigidBodySet.size().toInt()

        override val activeCount: Int
            get() = islands.activeDynamicBodies.size

        override fun all(): Collection<RigidBody> {
            return rigidBodySet.all().map {
                RapierBody(RapierBody.State.Added(
                    space = this@RapierSpace,
                    handle = RigidBodyHandle(it.handle),
                ))
            }
        }

        override fun active(): Collection<RigidBody> {
            return islands.activeDynamicBodies.map {
                RapierBody(RapierBody.State.Added(
                    space = this@RapierSpace,
                    handle = RigidBodyHandle(it),
                ))
            }
        }

        override fun add(value: RigidBody) {
            value as RapierBody
            when (val state = value.state) {
                is RapierBody.State.Added -> throw IllegalStateException("$value is attempting to be added to ${this@RapierSpace} but is already added to ${state.space}")
                is RapierBody.State.Removed -> {
                    val handle = rigidBodySet.insert(state.body)
                    value.state = RapierBody.State.Added(this@RapierSpace, RigidBodyHandle(handle))
                }
            }
        }

        override fun remove(value: RigidBody) {
            value as RapierBody
            when (val state = value.state) {
                is RapierBody.State.Added -> {
                    if (this@RapierSpace != state.space)
                        throw IllegalStateException("$value is attempting to be removed from ${this@RapierSpace} but is added to ${state.space}")
                    val body = rigidBodySet.remove(
                        state.handle.id,
                        islands,
                        colliderSet,
                        impulseJointSet,
                        multibodyJointSet,
                        false,
                    ) ?: throw IllegalStateException("$value does not exist in ${this@RapierSpace}")
                    value.state = RapierBody.State.Removed(body)
                }
                is RapierBody.State.Removed -> throw IllegalStateException("$value is not added to a space")
            }
        }
    }

    override val impulseJoints = object : PhysicsSpace.JointContainer<ImpulseJoint> {
        override val count: Int
            get() = impulseJointSet.size().toInt()

        override fun all(): Collection<ImpulseJoint> {
            return impulseJointSet.all().map {
                RapierJoint(RapierJoint.State.Impulse(
                    space = this@RapierSpace,
                    handle = JointHandle(it.handle),
                ))
            }
        }

        override fun add(value: ImpulseJoint, bodyA: RigidBody, bodyB: RigidBody) {
            value as RapierJoint
            bodyA as RapierBody
            bodyB as RapierBody
            when (val state = value.state) {
                is RapierJoint.State.Impulse -> throw IllegalStateException("$value is attempting to be added to ${this@RapierSpace} but is already added to ${state.space} as an impulse joint")
                is RapierJoint.State.Multibody -> throw IllegalStateException("$value is attempting to be added to ${this@RapierSpace} but is already added to ${state.space} as a multibody joint")
                is RapierJoint.State.Removed -> {
                    TODO()
                }
            }
        }

        override fun remove(value: ImpulseJoint) {
            TODO("Not yet implemented")
        }
    }

    override val multibodyJoints: PhysicsSpace.JointContainer<MultibodyJoint>
        get() = TODO("Not yet implemented")

    override fun toString() = "RapierSpace[0x%x]".format(pipeline.addr())

    override fun equals(other: Any?) = other is RapierSpace && pipeline.memory() == other.pipeline.memory()

    override fun hashCode() = pipeline.memory().hashCode()
}
