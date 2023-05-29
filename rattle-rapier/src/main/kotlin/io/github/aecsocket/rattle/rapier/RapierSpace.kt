package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.RigidBody
import rapier.dynamics.*
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

    fun createIntegrationParametersDesc() = IntegrationParametersDesc.ofDefault(arena).apply {
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

    override val colliders = object : PhysicsSpace.Container<Collider> {
        override val count: Int
            get() = colliderSet.size().toInt()

        override fun add(value: Collider) {
            value as RapierCollider
            when (val state = value.state) {
                is RapierCollider.State.Added -> throw IllegalStateException("$value is attempting to be added to ${this@RapierSpace} but is already added to ${state.space}")
                is RapierCollider.State.Removed -> {
                    val handle = colliderSet.insert(state.coll)
                    value.state = RapierCollider.State.Added(this@RapierSpace, ColliderHandle(ArenaKey(handle)))
                }
            }
        }

        override fun remove(value: Collider) {
            value as RapierCollider
            when (val state = value.state) {
                is RapierCollider.State.Added -> {
                    if (this@RapierSpace != state.space)
                        throw IllegalStateException("$value is attempting to be removed from ${this@RapierSpace} but is added to ${state.space}")
                    val coll = colliderSet.remove(
                        state.handle.key.id,
                        islands,
                        rigidBodySet,
                        false,
                    ) ?: throw IllegalStateException("$value does not exist in ${this@RapierSpace}")
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
            get() = count // TODO

        override fun add(value: RigidBody) {
            value as RapierBody
            when (val state = value.state) {
                is RapierBody.State.Added -> throw IllegalStateException("$value is attempting to be added to ${this@RapierSpace} but is already added to ${state.space}")
                is RapierBody.State.Removed -> {
                    val handle = rigidBodySet.insert(state.body)
                    value.state = RapierBody.State.Added(this@RapierSpace, RigidBodyHandle(ArenaKey(handle)))
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
                        state.handle.key.id,
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

    override fun toString() = "RapierSpace[0x%x]".format(pipeline.address())

    override fun equals(other: Any?) = other is RapierSpace && pipeline.memory() == other.pipeline.memory()

    override fun hashCode() = pipeline.memory().hashCode()
}
