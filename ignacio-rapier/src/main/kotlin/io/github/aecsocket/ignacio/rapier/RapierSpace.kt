package io.github.aecsocket.ignacio.rapier

import io.github.aecsocket.ignacio.*
import rapier.dynamics.*
import rapier.geometry.BroadPhase
import rapier.geometry.ColliderSet
import rapier.geometry.NarrowPhase
import rapier.pipeline.PhysicsPipeline
import rapier.pipeline.QueryPipeline
import java.lang.foreign.Arena

class RapierSpace internal constructor(
    val engine: RapierEngine,
    settings: PhysicsSpace.Settings,
) : PhysicsSpace {
    private val destroyed = DestroyFlag()

    val arena = Arena.openShared()

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

    val integrationParametersDesc = IntegrationParametersDesc.ofDefault(arena).apply {
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
    val gravity = settings.gravity.toVector(arena)

    override var settings = settings
        set(value) {
            field = value
            gravity.copyFrom(settings.gravity)
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

    override fun startStep(dt: Real) {
        val integrationParameters = integrationParametersDesc.apply {
            this.dt = dt
            minCcdDt = dt * engine.settings.integration.minCcdDtMultiplier
        }.build()
        pipeline.step(
            gravity,
            integrationParameters,
            islands,
            broadPhase,
            narrowPhase,
            rigidBodySet,
            colliderSet,
            impulseJointSet,
            multibodyJointSet,
            ccdSolver,
            queryPipeline,
        )
        integrationParameters.drop()
    }

    override fun finishStep() {}

    override fun toString() = "RapierSpace[0x%x]".format(pipeline.memory().address())

    override fun equals(other: Any?) = other is RapierSpace && pipeline.memory() == other.pipeline.memory()

    override fun hashCode() = pipeline.memory().hashCode()
}
