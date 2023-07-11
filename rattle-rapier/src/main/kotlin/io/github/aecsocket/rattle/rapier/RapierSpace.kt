package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.kbeam.EventDispatch
import io.github.aecsocket.klam.*
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.ContactManifold
import rapier.Native
import rapier.dynamics.CCDSolver
import rapier.dynamics.IslandManager
import rapier.dynamics.RigidBodySet
import rapier.dynamics.joint.impulse.ImpulseJointSet
import rapier.dynamics.joint.multibody.MultibodyJointSet
import rapier.geometry.BroadPhase
import rapier.geometry.ColliderSet
import rapier.geometry.NarrowPhase
import rapier.pipeline.*
import java.util.concurrent.locks.ReentrantLock

interface RapierPhysicsNative {
    var space: RapierSpace?
}

class RapierSpace internal constructor(
    val engine: RapierEngine,
    override var settings: PhysicsSpace.Settings,
) : RapierNative(), PhysicsSpace {
    override val nativeType get() = "RapierSpace"

    private val destroyed = DestroyFlag()

    override var lock: ReentrantLock? = null

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

    override val onCollision = EventDispatch<PhysicsSpace.OnCollision>()
    override val onContactForce = EventDispatch<PhysicsSpace.OnContactForce>()
    override val onFilterContactPair = EventDispatch<PhysicsSpace.OnFilterContactPair>()
    override val onFilterIntersectionPair = EventDispatch<PhysicsSpace.OnFilterIntersectionPair>()
    override val onModifySolverContacts = EventDispatch<PhysicsSpace.OnModifySolverContacts>()

    val events = object : EventHandler {
        override fun handleCollisionEvent(
            bodies: RigidBodySet,
            colliders: ColliderSet,
            event: CollisionEvent,
            contactPair: ContactPair?,
        ) {
            onCollision.dispatch(when (event) {
                is CollisionEvent.Started -> PhysicsSpace.OnCollision(
                    state = PhysicsSpace.OnCollision.State.STARTED,
                    colliderA = RapierColliderKey(event.coll1),
                    colliderB = RapierColliderKey(event.coll2),
                    manifolds = emptyList(), // todo
                )
                is CollisionEvent.Stopped -> PhysicsSpace.OnCollision(
                    state = PhysicsSpace.OnCollision.State.STOPPED,
                    colliderA = RapierColliderKey(event.coll1),
                    colliderB = RapierColliderKey(event.coll2),
                    manifolds = emptyList(), // todo
                )
            })
        }

        override fun handleContactForceEvent(
            dt: Double,
            bodies: RigidBodySet,
            colliders: ColliderSet,
            contactPair: ContactPair,
            totalForceMagnitude: Double
        ) {
            onContactForce.dispatch(PhysicsSpace.OnContactForce(
                dt = dt,
                totalMagnitude = totalForceMagnitude,
                colliderA = RapierColliderKey(contactPair.collider1),
                colliderB = RapierColliderKey(contactPair.collider2),
                manifolds = emptyList(), // TODO
            ))
        }
    }

    val hooks = object : PhysicsHooks {
        override fun filterContactPair(context: PairFilterContext): Int {
            return onFilterContactPair.dispatch(PhysicsSpace.OnFilterContactPair(
                colliderA = RapierColliderKey(context.collider1),
                colliderB = RapierColliderKey(context.collider2),
                bodyA = context.rigidBody1?.let { RapierRigidBodyKey(it) },
                bodyB = context.rigidBody2?.let { RapierRigidBodyKey(it) },
            )).solverFlags.flags
        }

        override fun filterIntersectionPair(context: PairFilterContext): Boolean {
            return onFilterIntersectionPair.dispatch(PhysicsSpace.OnFilterIntersectionPair(
                colliderA = RapierColliderKey(context.collider1),
                colliderB = RapierColliderKey(context.collider2),
                bodyA = context.rigidBody1?.let { RapierRigidBodyKey(it) },
                bodyB = context.rigidBody2?.let { RapierRigidBodyKey(it) },
            )).createPair
        }

        override fun modifySolverContacts(context: ContactModificationContext) {
            val res = onModifySolverContacts.dispatch(PhysicsSpace.OnModifySolverContacts(
                colliderA = RapierColliderKey(context.collider1),
                colliderB = RapierColliderKey(context.collider2),
                bodyA = context.rigidBody1?.let { RapierRigidBodyKey(it) },
                bodyB = context.rigidBody2?.let { RapierRigidBodyKey(it) },
                manifold = object : ContactManifold {}, // TODO
                normal = context.normal.toVec()
            ))
            context.normal = res.normal.toVector()
        }
    }

    override val handle: Native
        get() = pipeline

    private fun checkLock() = checkLock("space", lock)

    override fun destroy() {
        checkLock()
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
    }

    private fun assignSpace(obj: RapierPhysicsNative) {
        obj.space?.let { existing ->
            throw IllegalStateException("$obj is attempting to be added to $this but is already in $existing")
        }
        obj.space = this
    }

    override val colliders = object : PhysicsSpace.ColliderContainer {
        override val count: Int
            get() {
                checkLock()
                return colliderSet.size().toInt()
            }

        override fun read(key: ColliderKey): Collider? {
            checkLock()
            key as RapierColliderKey
            return colliderSet.get(key.handle)?.let { RapierCollider.Read(it, this@RapierSpace) }
        }

        override fun write(key: ColliderKey): Collider.Mut? {
            checkLock()
            key as RapierColliderKey
            return colliderSet.getMut(key.handle)?.let { RapierCollider.Write(it, this@RapierSpace) }
        }

        override fun all(): Collection<ColliderKey> {
            checkLock()
            return colliderSet.all().map { RapierColliderKey(it.handle) }
        }

        override fun add(value: Collider.Own): ColliderKey {
            checkLock()
            value as RapierCollider.Write
            assignSpace(value)
            return RapierColliderKey(colliderSet.insert(value.handle))
        }

        override fun remove(key: ColliderKey): Collider.Own? {
            checkLock()
            key as RapierColliderKey
            return colliderSet.remove(
                key.handle,
                islands,
                rigidBodySet,
                false,
            )?.let { RapierCollider.Write(it, space = null) }
        }

        override fun attach(coll: ColliderKey, to: RigidBodyKey) {
            checkLock()
            coll as RapierColliderKey
            to as RapierRigidBodyKey
            colliderSet.setParent(coll.handle, to.handle, rigidBodySet)
        }

        override fun detach(coll: ColliderKey) {
            checkLock()
            coll as RapierColliderKey
            colliderSet.setParent(coll.handle, null, rigidBodySet)
        }
    }

    override val rigidBodies = object : PhysicsSpace.ActiveContainer<RigidBody, RigidBody.Mut, RigidBody.Own, RigidBodyKey> {
        override val count: Int
            get() {
                checkLock()
                return rigidBodySet.size().toInt()
            }

        override val activeCount: Int
            get() {
                checkLock()
                return islands.activeDynamicBodies.size
            }

        override fun read(key: RigidBodyKey): RigidBody? {
            checkLock()
            key as RapierRigidBodyKey
            return rigidBodySet.get(key.handle)?.let { RapierRigidBody.Read(it, this@RapierSpace) }
        }

        override fun write(key: RigidBodyKey): RigidBody.Mut? {
            checkLock()
            key as RapierRigidBodyKey
            return rigidBodySet.getMut(key.handle)?.let { RapierRigidBody.Write(it, this@RapierSpace) }
        }

        override fun all(): Collection<RigidBodyKey> {
            checkLock()
            return rigidBodySet.all().map { RapierRigidBodyKey(it.handle) }
        }

        override fun active(): Collection<RigidBodyKey> {
            checkLock()
            return islands.activeDynamicBodies.map { RapierRigidBodyKey(it) }
        }

        override fun add(value: RigidBody.Own): RigidBodyKey {
            checkLock()
            value as RapierRigidBody.Write
            assignSpace(value)
            return RapierRigidBodyKey(rigidBodySet.insert(value.handle))
        }

        override fun remove(key: RigidBodyKey): RigidBody.Own? {
            checkLock()
            key as RapierRigidBodyKey
            return rigidBodySet.remove(
                key.handle,
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
            get() {
                checkLock()
                return impulseJointSet.size().toInt()
            }

        override fun read(key: ImpulseJointKey): ImpulseJoint? {
            checkLock()
            key as RapierImpulseJointKey
            return impulseJointSet.get(key.handle)?.let { RapierImpulseJoint.Read(it, this@RapierSpace) }
        }

        override fun write(key: ImpulseJointKey): ImpulseJoint.Mut? {
            checkLock()
            key as RapierImpulseJointKey
            return impulseJointSet.getMut(key.handle)?.let { RapierImpulseJoint.Write(it, this@RapierSpace) }
        }

        override fun all(): Collection<ImpulseJointKey> {
            checkLock()
            return impulseJointSet.all().map { RapierImpulseJointKey(it.handle) }
        }

        override fun add(value: Joint.Own, bodyA: RigidBodyKey, bodyB: RigidBodyKey): ImpulseJointKey {
            checkLock()
            value as RapierJoint.Write
            bodyA as RapierRigidBodyKey
            bodyB as RapierRigidBodyKey
            assignSpace(value)
            return RapierImpulseJointKey(impulseJointSet.insert(bodyA.handle, bodyB.handle, value.handle, false))
        }

        override fun remove(key: ImpulseJointKey): Joint.Own? {
            checkLock()
            key as RapierImpulseJointKey
            return impulseJointSet.remove(
                key.handle,
                false,
            )?.let {
                // `.remove` returns an ImpulseJoint.Mut, which contains some extra data relating to that joint
                // we only want to return the `GenericJoint`, so we use a little hack and have `.retainData()`,
                // a native function designed specifically to discard everything apart from the GenericJoint
                RapierJoint.Write(it.retainData(), space = null)
            }
        }
    }

    override val multibodyJoints = object : PhysicsSpace.MultibodyJointContainer {
        override fun add(value: Joint.Own, bodyA: RigidBodyKey, bodyB: RigidBodyKey) {
            checkLock()
            value as RapierJoint.Write
            bodyA as RapierRigidBodyKey
            bodyB as RapierRigidBodyKey
            assignSpace(value)
            multibodyJointSet.insert(bodyA.handle, bodyB.handle, value.handle, false)
        }

        override fun removeOn(bodyKey: RigidBodyKey) {
            checkLock()
            bodyKey as RapierRigidBodyKey
            multibodyJointSet.removeJointsAttachedToRigidBody(bodyKey.handle)
        }
    }

    override val query = object : PhysicsQuery {
        override fun intersectBounds(
            bounds: DAabb3,
            fn: (ColliderKey) -> PhysicsQuery.Result,
        ) {
            checkLock()
            queryPipeline.collidersWithAabbIntersectingAabb(
                bounds.toRapier(),
            ) {
                fn(RapierColliderKey(it)) == PhysicsQuery.Result.CONTINUE
            }
        }

        override fun intersectPoint(
            point: DVec3,
            filter: PhysicsQuery.Filter,
            fn: (ColliderKey) -> PhysicsQuery.Result,
        ) {
            checkLock()
            queryPipeline.intersectionsWithPoint(
                rigidBodySet,
                colliderSet,
                point.toVector(),
                filter.toRapier(this@RapierSpace),
            ) {
                fn(RapierColliderKey(it)) == PhysicsQuery.Result.CONTINUE
            }
        }

        override fun intersectShape(
            shape: Shape,
            shapePos: DIso3,
            filter: PhysicsQuery.Filter,
            fn: (ColliderKey) -> PhysicsQuery.Result,
        ) {
            checkLock()
            shape as RapierShape
            queryPipeline.intersectionsWithShape(
                rigidBodySet,
                colliderSet,
                shapePos.toIsometry(),
                shape.handle,
                filter.toRapier(this@RapierSpace),
            ) {
                fn(RapierColliderKey(it)) == PhysicsQuery.Result.CONTINUE
            }
        }

        override fun intersectShapeFirst(
            shape: Shape,
            shapePos: DIso3,
            filter: PhysicsQuery.Filter,
        ): ColliderKey? {
            checkLock()
            shape as RapierShape
            return queryPipeline.intersectionWithShape(
                rigidBodySet,
                colliderSet,
                shapePos.toIsometry(),
                shape.handle,
                filter.toRapier(this@RapierSpace),
            )?.let { RapierColliderKey(it) }
        }

        override fun castRay(
            ray: DRay3,
            maxDistance: Double,
            settings: PhysicsQuery.RayCastSettings,
            filter: PhysicsQuery.Filter,
            fn: (PhysicsQuery.RayCast) -> PhysicsQuery.Result,
        ) {
            checkLock()
            queryPipeline.intersectionWithRay(
                rigidBodySet,
                colliderSet,
                ray.toRapier(),
                maxDistance,
                settings.isSolid,
                filter.toRapier(this@RapierSpace),
            ) {
                fn(it.toRattle()) == PhysicsQuery.Result.CONTINUE
            }
        }

        override fun castRayFirst(
            ray: DRay3,
            maxDistance: Double,
            settings: PhysicsQuery.RayCastSettings,
            filter: PhysicsQuery.Filter,
        ): PhysicsQuery.RayCast? {
            checkLock()
            return queryPipeline.castRayAndGetNormal(
                rigidBodySet,
                colliderSet,
                ray.toRapier(),
                maxDistance,
                settings.isSolid,
                filter.toRapier(this@RapierSpace),
            )?.toRattle()
        }

        override fun castShape(
            shape: Shape,
            shapePos: DIso3,
            shapeDir: DVec3,
            maxDistance: Double,
            settings: PhysicsQuery.ShapeCastSettings,
            filter: PhysicsQuery.Filter
        ): PhysicsQuery.ShapeCast? {
            checkLock()
            shape as RapierShape
            return queryPipeline.castShape(
                rigidBodySet,
                colliderSet,
                shapePos.toIsometry(),
                shapeDir.toVector(),
                shape.handle,
                maxDistance,
                settings.stopAtPenetration,
                filter.toRapier(this@RapierSpace),
            )?.toRattle()
        }

        override fun castShapeNonLinear(
            shape: Shape,
            shapePos: DIso3,
            shapeLocalCenter: DVec3,
            shapeLinVel: DVec3,
            shapeAngVel: DVec3,
            timeStart: Double,
            timeEnd: Double,
            settings: PhysicsQuery.ShapeCastSettings,
            filter: PhysicsQuery.Filter
        ): PhysicsQuery.ShapeCast? {
            checkLock()
            shape as RapierShape
            val motion = NonlinearRigidMotion(
                shapePos.toIsometry(),
                shapeLocalCenter.toVector(),
                shapeLinVel.toVector(),
                shapeAngVel.toAngVector()
            )
            return queryPipeline.nonlinearCastShape(
                rigidBodySet,
                colliderSet,
                motion,
                shape.handle,
                timeStart,
                timeEnd,
                settings.stopAtPenetration,
                filter.toRapier(this@RapierSpace)
            )?.toRattle()
        }

        override fun projectPoint(
            point: DVec3,
            filter: PhysicsQuery.Filter
        ): PhysicsQuery.PointProject? {
            checkLock()
            return queryPipeline.projectPointAndGetFeature(
                rigidBodySet,
                colliderSet,
                point.toVector(),
                filter.toRapier(this@RapierSpace),
            )?.toRattle()
        }
    }
}
