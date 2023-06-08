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
import rapier.pipeline.*

interface RapierPhysicsNative {
    var space: RapierSpace?
}

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

    val events = EventHandler.of(arena, object : EventHandler.Fn {
        override fun handleCollisionEvent(
            bodies: RigidBodySet,
            colliders: ColliderSet,
            event: CollisionEvent,
            contactPair: ContactPair
        ) {
            // todo
        }

        override fun handleContactForceEvent(
            dt: Double,
            bodies: RigidBodySet,
            colliders: ColliderSet,
            contactPair: ContactPair,
            totalForceMagnitude: Double
        ) {
            // todo
        }
    })

    val hooks = PhysicsHooks.of(arena, object : PhysicsHooks.Fn {
        override fun filterContactPair(context: PairFilterContext): Int {
            // todo
            return SolverFlags.COMPUTE_IMPULSES
        }

        override fun filterIntersectionPair(context: PairFilterContext): Boolean {
            // todo
            return true
        }

        override fun modifySolverContacts(context: ContactModificationContext) {
            // todo
        }
    })

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

    private fun assignSpace(obj: RapierPhysicsNative) {
        obj.space?.let { existing ->
            throw IllegalStateException("$obj is attempting to be added to $this but is already in $existing")
        }
        obj.space = this
    }

    override val colliders = object : PhysicsSpace.SimpleContainer<Collider, Collider.Mut, Collider.Own, ColliderKey> {
        override val count: Int
            get() = colliderSet.size().toInt()

        override fun read(key: ColliderKey): Collider? {
            key as RapierColliderKey
            return colliderSet.get(key.id)?.let { RapierCollider.Read(it, this@RapierSpace) }
        }

        override fun write(key: ColliderKey): Collider.Mut? {
            key as RapierColliderKey
            return colliderSet.getMut(key.id)?.let { RapierCollider.Write(it, this@RapierSpace) }
        }

        override fun all(): Collection<ColliderKey> {
            return colliderSet.all().map { RapierColliderKey(it.handle) }
        }

        override fun add(value: Collider.Own): ColliderKey {
            value as RapierCollider.Write
            assignSpace(value)
            return RapierColliderKey(colliderSet.insert(value.handle))
        }

        override fun remove(key: ColliderKey): Collider.Own? {
            key as RapierColliderKey
            return colliderSet.remove(
                key.id,
                islands,
                rigidBodySet,
                false,
            )?.let { RapierCollider.Write(it, space = null) }
        }
    }

    override val rigidBodies = object : PhysicsSpace.ActiveContainer<RigidBody, RigidBody.Mut, RigidBody.Own, RigidBodyKey> {
        override val count: Int
            get() = rigidBodySet.size().toInt()

        override val activeCount: Int
            get() = islands.activeDynamicBodies.size

        override fun read(key: RigidBodyKey): RigidBody? {
            key as RapierRigidBodyKey
            return rigidBodySet.get(key.id)?.let { RapierRigidBody.Read(it, this@RapierSpace) }
        }

        override fun write(key: RigidBodyKey): RigidBody.Mut? {
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
            value as RapierRigidBody.Write
            assignSpace(value)
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
            )?.let { RapierRigidBody.Write(it, space = null) }
        }
    }

    override val impulseJoints = object : PhysicsSpace.ImpulseJointContainer {
        override val count: Int
            get() = impulseJointSet.size().toInt()

        override fun read(key: ImpulseJointKey): ImpulseJoint? {
            key as RapierImpulseJointKey
            return impulseJointSet.get(key.id)?.let { RapierImpulseJoint.Read(it, this@RapierSpace) }
        }

        override fun write(key: ImpulseJointKey): ImpulseJoint.Mut? {
            key as RapierImpulseJointKey
            return impulseJointSet.getMut(key.id)?.let { RapierImpulseJoint.Write(it, this@RapierSpace) }
        }

        override fun all(): Collection<ImpulseJointKey> {
            return impulseJointSet.all().map { RapierImpulseJointKey(it.handle) }
        }

        override fun add(value: Joint.Own, bodyA: RigidBodyKey, bodyB: RigidBodyKey): ImpulseJointKey {
            value as RapierJoint.Write
            bodyA as RapierRigidBodyKey
            bodyB as RapierRigidBodyKey
            assignSpace(value)
            return RapierImpulseJointKey(impulseJointSet.insert(bodyA.id, bodyB.id, value.handle, false))
        }

        override fun remove(key: ImpulseJointKey): Joint.Own? {
            key as RapierImpulseJointKey
            return impulseJointSet.remove(
                key.id,
                false,
            )?.let {
                // `.remove` returns an ImpulseJoint.Mut, which contains some extra data relating to that joint
                // we only want to return the `GenericJoint`, so we use a little hack and have `.retainData()`,
                // a native function designed specifically for our use
                RapierJoint.Write(it.retainData(), space = null)
            }
        }
    }

    override val multibodyJoints = object : PhysicsSpace.MultibodyJointContainer {
        override fun add(value: Joint.Own, bodyA: RigidBodyKey, bodyB: RigidBodyKey) {
            value as RapierJoint.Write
            bodyA as RapierRigidBodyKey
            bodyB as RapierRigidBodyKey
            assignSpace(value)
            multibodyJointSet.insert(bodyA.id, bodyB.id, value.handle, false)
        }

        override fun removeOn(bodyKey: RigidBodyKey) {
            bodyKey as RapierRigidBodyKey
            multibodyJointSet.removeJointsAttachedToRigidBody(bodyKey.id)
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
}
