package io.github.aecsocket.rattle

import io.github.aecsocket.alexandria.EventDispatch
import io.github.aecsocket.klam.*
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Required
import java.util.concurrent.locks.ReentrantLock

/**
 * A linear axis in 3D space.
 */
enum class LinAxis {
    X,
    Y,
    Z
}

/**
 * A degree of freedom in 3D space.
 */
enum class Dof {
    X,
    Y,
    Z,
    ANG_X,
    ANG_Y,
    ANG_Z,
}

/**
 * An independent object storing simulation data for a set of physics structures. This takes ownership of objects like
 * [RigidBody] and [Collider] instances, and allows manipulating and querying the internal structures. An instance
 * can be created through [PhysicsEngine.createSpace].
 */
interface PhysicsSpace : Destroyable {
    /**
     * Simulation parameters for a physics space.
     * @param gravity The gravity **acceleration** applied to all bodies, in meters/sec.
     */
    @ConfigSerializable
    data class Settings(
        val gravity: DVec3 = DVec3(0.0, -9.81, 0.0),
    )

    /**
     * Settings for the simulation of the physics space. Note that not all options will be configurable here, as
     * some are considered implementation details. In this case, the settings need to be configured with the actual
     * implementation.
     */
    var settings: Settings

    /**
     * The lock used for checking thread-safe access (see [PhysicsEngine]).
     */
    var lock: ReentrantLock?

    /**
     * Provides access to the [Collider] instances in this space.
     */
    val colliders: ColliderContainer

    /**
     * Provides access to the [RigidBody] instances in this space.
     */
    val rigidBodies: ActiveContainer<RigidBody, RigidBody.Mut, RigidBody.Own, RigidBodyKey>

    /**
     * Provides access to the [ImpulseJoint] instances in this space.
     */
    val impulseJoints: ImpulseJointContainer

    /**
     * Provides access to the [MultibodyJoint] instances in this space.
     */
    val multibodyJoints: MultibodyJointContainer

    interface SimpleContainer<R, W, O, K> {
        val count: Int

        fun all(): Collection<K>

        fun read(key: K): R?

        fun write(key: K): W?

        fun add(value: O): K

        fun remove(key: K): O?
    }

    interface ColliderContainer : SimpleContainer<Collider, Collider.Mut, Collider.Own, ColliderKey> {
        /**
         * Attaches a [Collider] to a [RigidBody] (see [Collider]).
         */
        fun attach(coll: ColliderKey, to: RigidBodyKey)

        /**
         * Detaches a [Collider] from any parent [RigidBody] (see [Collider]).
         */
        fun detach(coll: ColliderKey)
    }

    interface ActiveContainer<R, W, O, K> : SimpleContainer<R, W, O, K> {
        val activeCount: Int

        fun active(): Collection<K>
    }

    interface ImpulseJointContainer {
        val count: Int

        fun all(): Collection<ImpulseJointKey>

        fun read(key: ImpulseJointKey): ImpulseJoint?

        fun write(key: ImpulseJointKey): ImpulseJoint.Mut?

        fun add(value: Joint.Own, bodyA: RigidBodyKey, bodyB: RigidBodyKey): ImpulseJointKey

        fun remove(key: ImpulseJointKey): Joint.Own?
    }

    interface MultibodyJointContainer {
        fun add(value: Joint.Own, bodyA: RigidBodyKey, bodyB: RigidBodyKey)

        fun removeOn(bodyKey: RigidBodyKey)
    }

    /**
     * Provides access to performing queries on this physics space, such as raycasts.
     */
    val query: PhysicsQuery

    /**
     * Runs when a collision between two [Collider]s is either started or stopped.
     * Runs only when [ColliderEvent.COLLISION] is marked on at least one of the colliders.
     */
    val onCollision: EventDispatch<OnCollision>

    val onContactForce: EventDispatch<OnContactForce>

    val onFilterContactPair: EventDispatch<OnFilterContactPair>

    val onFilterIntersectionPair: EventDispatch<OnFilterIntersectionPair>

    val onModifySolverContacts: EventDispatch<OnModifySolverContacts>

    interface Manifold {

    }

    data class OnCollision(
        val state: State,
        val colliderA: ColliderKey,
        val colliderB: ColliderKey,
        val manifolds: List<Manifold>,
    ) {
        enum class State {
            STARTED,
            STOPPED,
        }
    }

    data class OnContactForce(
        val dt: Double,
        val totalMagnitude: Double,
        val colliderA: ColliderKey,
        val colliderB: ColliderKey,
        val manifolds: List<Manifold>,
    )

    data class OnFilterContactPair(
        val colliderA: ColliderKey,
        val colliderB: ColliderKey,
        val rigidBodyA: RigidBodyKey?,
        val rigidBodyB: RigidBodyKey?,
        var solverFlags: SolverFlags = SolverFlags.COMPUTE_IMPULSES,
    )

    @JvmInline
    value class SolverFlags(val flags: Int) {
        companion object {
            val COMPUTE_IMPULSES = SolverFlags(1)
        }

        infix fun or(rhs: SolverFlags) = SolverFlags(flags or rhs.flags)

        infix fun and(rhs: SolverFlags) = SolverFlags(flags and rhs.flags)
    }

    data class OnFilterIntersectionPair(
        val colliderA: ColliderKey,
        val colliderB: ColliderKey,
        val rigidBodyA: RigidBodyKey?,
        val rigidBodyB: RigidBodyKey?,
        var createPair: Boolean = true,
    )

    data class OnModifySolverContacts(
        val colliderA: ColliderKey,
        val colliderB: ColliderKey,
        val rigidBodyA: RigidBodyKey?,
        val rigidBodyB: RigidBodyKey?,
        val manifold: Manifold,
        // TODO solverContacts
        var normal: DVec3,
    )
}


interface PhysicsQuery {
    enum class Result {
        CONTINUE,
        STOP,
    }

    data class Filter(
        val flags: QueryFilterFlags = QueryFilterFlags.fromRaw(0),
        val group: InteractionGroup = InteractionGroup.All,
        val excludeCollider: ColliderKey? = null,
        val excludeRigidBody: RigidBodyKey? = null,
        val predicate: ((coll: Collider, collKey: ColliderKey) -> Result)? = null,
    )

    @ConfigSerializable
    data class RayCastSettings(
        @Required val isSolid: Boolean,
    )

    @ConfigSerializable
    data class ShapeCastSettings(
        @Required val stopAtPenetration: Boolean,
    )

    @ConfigSerializable
    data class PointProjectSettings(
        @Required val isSolid: Boolean,
    )

    @JvmInline
    value class QueryFilterFlags private constructor(val raw: Int) {
        companion object {
            fun fromRaw(raw: Int) = QueryFilterFlags(raw)
        }

        fun raw() = raw

        infix fun or(rhs: QueryFilterFlags) = QueryFilterFlags(raw or rhs.raw)
    }

    sealed interface ShapeFeature {
        data class Vertex(val id: Int) : ShapeFeature

        data class Edge(val id: Int) : ShapeFeature

        data class Face(val id: Int) : ShapeFeature
    }

    sealed interface Intersect {
        /* TODO: Kotlin 1.9 data */ object Penetrating : Intersect

        data class Separated(
            val state: State,
            val time: Double,
            val localPointA: DVec3,
            val localPointB: DVec3,
            val normalA: DVec3,
            val normalB: DVec3,
        ) : Intersect

        enum class State {
            CONVERGED,
            OUT_OF_ITERATIONS,
            FAILED,
        }
    }

    data class RayCast(
        val collider: ColliderKey,
        val hitTime: Double,
        val normal: DVec3,
        val feature: ShapeFeature,
    )

    data class ShapeCast(
        val collider: ColliderKey,
        val intersect: Intersect,
    )

    data class PointProject(
        val collider: ColliderKey,
        val point: DVec3,
        val wasInside: Boolean,
        val feature: ShapeFeature,
    )

    data class ContactPair(
        val colliderA: ColliderKey,
        val colliderB: ColliderKey,
        val manifolds: List<PhysicsSpace.Manifold>,
    )


    /**
     * Runs [fn] for every collider whose broad-phase bounds intersect with [bounds].
     *
     * This is the fastest and cheapest query, as it only reads the broad-phase. Use this if you only need a coarse
     * set of colliders in a general area.
     * @param bounds The axis-aligned bounds to test against.
     * @param fn The callback to run for each result.
     */
    fun intersectBounds(
        bounds: DAabb3,
        fn: (ColliderKey) -> Result,
    )

    /**
     * Returns the first collider for which [point] is inside of it.
     *
     * This is cheaper than the method of the same name which takes a callback function, so prefer using this if you
     * only need one result.
     * @param point The point to test against.
     * @param filter The filter for determining which colliders are tested against.
     */
    fun intersectPoint(
        point: DVec3,
        filter: Filter,
    ): ColliderKey?

    /**
     * Runs [fn] for every collider for which [point] is inside of it.
     *
     * This is more expensive than the method of the same name which returns a result, so prefer using that if you only
     * need one result.
     * @param point The point to test against.
     * @param filter The filter for determining which colliders are tested against.
     * @param fn The callback to run for each result.
     */
    fun intersectPoint(
        point: DVec3,
        filter: Filter,
        fn: (ColliderKey) -> Result,
    )

    /**
     * Returns the first collider whose shape intersects [shape], when that shape is positioned at [shapePos].
     *
     * This is cheaper than the method of the same name which takes a callback function, so prefer using this if you
     * only need one result.
     * @param shape The shape to test against.
     * @param shapePos The position of the tested shape in the world.
     * @param filter The filter for determining which colliders are tested against.
     */
    fun intersectShape(
        shape: Shape,
        shapePos: DIso3,
        filter: Filter
    ): ColliderKey?

    /**
     * Runs [fn] for every collider whose shape intersects [shape], when that shape is positioned at [shapePos].
     *
     * This is more expensive than the method of the same name which returns a result, so prefer using that if you only
     * need one result.
     * @param shape The shape to test against.
     * @param shapePos The position of the tested shape in the world.
     * @param filter The filter for determining which colliders are tested against.
     * @param fn The callback to run for each result.
     */
    fun intersectShape(
        shape: Shape,
        shapePos: DIso3,
        filter: Filter,
        fn: (ColliderKey) -> Result,
    )

    /**
     * Finds the first collider in an intersection between it and [ray], reaching colliders up to a maximum of
     * [maxDistance] units away.
     *
     * This is cheaper than the method of the same name which takes a callback function, so prefer using this if you
     * only need one result.
     * @param ray The ray to test against.
     * @param maxDistance The maximum distance the ray will hit colliders.
     * @param settings Settings on how the raycast will behave.
     * @param filter The filter for determining which colliders are tested against.
     */
    fun rayCast(
        ray: DRay3,
        maxDistance: Double,
        settings: RayCastSettings,
        filter: Filter,
    ): RayCast?

    /**
     * Runs [fn] for every collider found in an intersection between it and [ray], reaching colliders up to a maximum of
     * [maxDistance] units away.
     *
     * This is more expensive than the method of the same name which returns a result, so prefer using that if you only
     * need one result.
     * @param maxDistance The maximum distance the ray will hit colliders.
     * @param settings Settings on how the raycast will behave.
     * @param filter The filter for determining which colliders are tested against.
     */
    fun rayCast(
        ray: DRay3,
        maxDistance: Double,
        settings: RayCastSettings,
        filter: Filter,
        fn: (RayCast) -> Result,
    )

    /**
     * Finds the first collider for which [shape], when cast (or "swept") across from [shapePos] in the direction of
     * [shapeDir] units up to a distance of [maxDistance], will collide with the collider's shape.
     *
     * This is the cheapest shape cast method, which does not take rotational velocity into account.
     * @param shape The shape to cast.
     * @param shapePos The position the shape starts at.
     * @param shapeDir The direction which the shape moves in.
     * @param maxDistance How far the shape will move in the [shapeDir] direction.
     * @param settings Settings on how the shape cast will behave.
     * @param filter The filter for determining which colliders are tested against.
     */
    fun shapeCast(
        shape: Shape,
        shapePos: DIso3,
        shapeDir: DVec3,
        maxDistance: Double,
        settings: ShapeCastSettings,
        filter: Filter,
    ): ShapeCast?

    /**
     * Finds the first collider for which [shape], when cast (or "swept") across from [shapePos] in the motion specified
     * by [shapeLinVel] and [shapeAngVel], will collide with the collider's shape.
     *
     * This is more expensive than [shapeCast] as it also takes rotational velocity into account.
     * @param shape The shape to cast.
     * @param shapePos The position the shape starts at.
     * @param shapeLocalCenter The local-space point at which the rotational part of motion is applied.
     * @param shapeLinVel The translational velocity of this shape motion.
     * @param shapeAngVel The rotational velocity of this shape motion.
     * @param timeStart The starting time of the shape cast motion.
     * @param timeEnd The ending time of the shape cast motion.
     * @param settings Settings on how the shape cast will behave.
     * @param filter The filter for determining which colliders are tested against.
     */
    fun shapeCastNonLinear(
        shape: Shape,
        shapePos: DIso3,
        shapeLocalCenter: DVec3,
        shapeLinVel: DVec3,
        shapeAngVel: DVec3,
        timeStart: Double,
        timeEnd: Double,
        settings: ShapeCastSettings,
        filter: Filter,
    ): ShapeCast?

    /**
     * Projects a point onto the closest collider to that point.
     * @param point The point in world-space to project.
     * @param settings Settings on how the point projection will behave.
     * @param filter The filter for determining which colliders are tested against.
     */
    fun projectPoint(
        point: DVec3,
        settings: PointProjectSettings,
        filter: Filter,
    ): PointProject?
}
