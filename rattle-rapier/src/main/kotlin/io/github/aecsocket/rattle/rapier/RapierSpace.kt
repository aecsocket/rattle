package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.RigidBody
import rapier.Native
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
) : RapierNative(), PhysicsSpace {
    override val nativeType get() = "RapierSpace"

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

    override val handle: Native
        get() = pipeline

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

    override val colliders = object : PhysicsSpace.SingleContainer<Collider.Read, Collider.Write, Collider.Own, ColliderKey> {
        override val count: Int
            get() = colliderSet.size().toInt()

        override fun read(key: ColliderKey): Collider.Read? {
            key as RapierColliderKey
            return colliderSet.get(key.id)?.let { RapierCollider.Read(it, this@RapierSpace) }
        }

        override fun write(key: ColliderKey): Collider.Write? {
            key as RapierColliderKey
            return colliderSet.getMut(key.id)?.let { RapierCollider.Write(it, this@RapierSpace) }
        }

        override fun all(): Collection<ColliderKey> {
            return colliderSet.all().map { RapierColliderKey(it.handle) }
        }

        override fun add(value: Collider.Own): ColliderKey {
            value as RapierCollider.Own
            value.space?.let { existing ->
                throw IllegalStateException("$value is attempting to be added to ${this@RapierSpace} but is already in $existing")
            }
            value.space = this@RapierSpace
            return RapierColliderKey(colliderSet.insert(value.handle))
        }

        override fun remove(key: ColliderKey): Collider.Own? {
            key as RapierColliderKey
            return colliderSet.remove(
                key.id,
                islands,
                rigidBodySet,
                false,
            )?.let { RapierCollider.Own(it, null) }
        }
    }

    override val bodies = object : PhysicsSpace.ActiveContainer<RigidBody.Read, RigidBody.Write, RigidBody.Own, RigidBodyKey> {
        override val count: Int
            get() = rigidBodySet.size().toInt()

        override val activeCount: Int
            get() = islands.activeDynamicBodies.size

        override fun read(key: RigidBodyKey): RigidBody.Read? {
            key as RapierRigidBodyKey
            return rigidBodySet.get(key.id)?.let { RapierRigidBody.Read(it, this@RapierSpace) }
        }

        override fun write(key: RigidBodyKey): RigidBody.Write? {
            key as RapierRigidBodyKey
            return rigidBodySet.getMut(key.id)?.let { RapierRigidBody.Write(it, this@RapierSpace) }
        }

        override fun all(): Collection<RigidBodyKey> {
            return rigidBodySet.all().map { RapierRigidBodyKey(it.handle) }
        }

        override fun active(): Collection<RigidBodyKey> {
            return islands.activeDynamicBodies.map { RapierRigidBodyKey(it) }
        }

        override fun add(value: RigidBody.Own): RigidBodyKey {
            value as RapierRigidBody.Own
            value.space?.let { existing ->
                throw IllegalStateException("$value is attempting to be added to ${this@RapierSpace} but is already in $existing")
            }
            value.space = this@RapierSpace
            return RapierRigidBodyKey(rigidBodySet.insert(value.handle))
        }

        override fun remove(key: RigidBodyKey): RigidBody.Own? {
            key as RapierRigidBodyKey
            return rigidBodySet.remove(
                key.id,
                islands,
                colliderSet,
                impulseJointSet,
                multibodyJointSet,
                false,
            )?.let { RapierRigidBody.Own(it, null) }
        }
    }

    override fun attach(coll: ColliderKey, to: RigidBodyKey) {
        coll as RapierColliderKey
        to as RapierRigidBodyKey
        colliderSet.setParent(coll.id, to.id, rigidBodySet)
    }

    override fun detach(coll: ColliderKey) {
        coll as RapierColliderKey
        colliderSet.setParent(coll.id, null, rigidBodySet)
    }

//
//    override val impulseJoints = object : PhysicsSpace.JointContainer<ImpulseJoint> {
//        override val count: Int
//            get() = impulseJointSet.size().toInt()
//
//        override fun all(): Collection<ImpulseJoint> {
//            return impulseJointSet.all().map {
//                RapierJoint(RapierJoint.State.Impulse(
//                    space = this@RapierSpace,
//                    handle = JointHandle(it.handle),
//                ))
//            }
//        }
//
//        override fun add(value: ImpulseJoint, bodyA: RigidBody, bodyB: RigidBody) {
//            value as RapierJoint
//            bodyA as RapierRigidBody
//            bodyB as RapierRigidBody
//            when (val state = value.state) {
//                is RapierJoint.State.Impulse -> throw IllegalStateException("$value is attempting to be added to ${this@RapierSpace} but is already added to ${state.space} as an impulse joint")
//                is RapierJoint.State.Multibody -> throw IllegalStateException("$value is attempting to be added to ${this@RapierSpace} but is already added to ${state.space} as a multibody joint")
//                is RapierJoint.State.Removed -> {
//                    TODO()
//                }
//            }
//        }
//
//        override fun remove(value: ImpulseJoint) {
//            TODO("Not yet implemented")
//        }
//    }
//
//    override val multibodyJoints: PhysicsSpace.JointContainer<MultibodyJoint>
//        get() = TODO("Not yet implemented")
}
