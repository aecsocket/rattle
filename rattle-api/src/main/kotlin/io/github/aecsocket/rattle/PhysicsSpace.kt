package io.github.aecsocket.rattle

import io.github.aecsocket.alexandria.EventDispatch
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Required

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

enum class QueryResult(val shouldContinue: Boolean) {
    CONTINUE    (true),
    STOP        (false),
}

@JvmInline
value class QueryFilterFlags private constructor(val raw: Int) {
    companion object {
        fun fromRaw(raw: Int) = QueryFilterFlags(raw)
    }

    fun raw() = raw

    infix fun or(rhs: QueryFilterFlags) = QueryFilterFlags(raw or rhs.raw)
}

data class QueryFilter(
    val flags: QueryFilterFlags = QueryFilterFlags.fromRaw(0),
    val group: InteractionGroup = InteractionGroup.All,
    val excludeCollider: ColliderKey? = null,
    val excludeRigidBody: RigidBodyKey? = null,
    val predicate: ((coll: Collider, collKey: ColliderKey) -> QueryResult)? = null,
)

sealed interface ShapeFeature {
    data class Vertex(val id: Int) : ShapeFeature

    data class Edge(val id: Int) : ShapeFeature

    data class Face(val id: Int) : ShapeFeature
}

sealed interface Intersect {
    /* TODO: Kotlin 1.9 data */ object Penetrating : Intersect

    data class Separated(
        val state: State,
        val time: Real,
        val localPointA: Vec,
        val localPointB: Vec,
        val normalA: Vec,
        val normalB: Vec,
    ) : Intersect

    enum class State {
        CONVERGED,
        OUT_OF_ITERATIONS,
        FAILED,
    }
}

sealed interface RayCast {
    val collider: ColliderKey
    val hitTime: Real

    data class Simple(
        override val collider: ColliderKey,
        override val hitTime: Real,
    ) : RayCast

    data class Complex(
        override val collider: ColliderKey,
        override val hitTime: Real,
        val normal: Vec,
        val feature: ShapeFeature,
    ) : RayCast
}

data class ShapeCast(
    val collider: ColliderKey,
    val intersect: Intersect,
)

sealed interface PointProject {
    val collider: ColliderKey
    val point: Vec
    val wasInside: Boolean

    data class Simple(
        override val collider: ColliderKey,
        override val wasInside: Boolean,
        override val point: Vec,
    ) : PointProject

    data class Complex(
        override val collider: ColliderKey,
        override val wasInside: Boolean,
        override val point: Vec,
        val feature: ShapeFeature,
    ) : PointProject
}

data class ContactPair(
    val colliderA: ColliderKey,
    val colliderB: ColliderKey,
    val manifolds: List<Manifold>,
)

interface Manifold {

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
        val gravity: Vec = Vec(0.0, -9.81, 0.0),
    )

    /**
     * Settings for the simulation of the physics space. Note that not all options will be configurable here, as
     * some are considered implementation details. In this case, the settings need to be configured with the actual
     * implementation.
     */
    var settings: Settings

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

    val query: Query
    interface Query {
        fun intersectBounds(
            bounds: Aabb,
            fn: (ColliderKey) -> QueryResult,
        )

        fun intersectPoint(
            point: Vec,
            filter: QueryFilter,
            fn: (ColliderKey) -> QueryResult,
        )

        fun intersectPoint(
            point: Vec,
            filter: QueryFilter,
        ): ColliderKey?

        fun intersectShape(
            shape: Shape,
            shapePos: Iso,
            filter: QueryFilter
        ): ColliderKey?

        fun intersectShape(
            shape: Shape,
            shapePos: Iso,
            filter: QueryFilter,
            fn: (ColliderKey) -> QueryResult,
        )

        fun rayCast(
            ray: Ray,
            maxDistance: Real,
            settings: RayCastSettings,
            filter: QueryFilter,
        ): RayCast.Simple?

        fun rayCastComplex(
            ray: Ray,
            maxDistance: Real,
            settings: RayCastSettings,
            filter: QueryFilter,
        ): RayCast.Complex?

        fun rayCastComplex(
            ray: Ray,
            maxDistance: Real,
            settings: RayCastSettings,
            filter: QueryFilter,
            fn: (RayCast.Complex) -> QueryResult,
        )

        fun shapeCast(
            shape: Shape,
            shapePos: Iso,
            shapeVel: Vec,
            maxDistance: Real,
            settings: ShapeCastSettings,
            filter: QueryFilter,
        ): ShapeCast?

        fun shapeCastNonLinear(
            shape: Shape,
            shapePos: Iso,
            shapeLocalCenter: Vec,
            shapeLinVel: Vec,
            shapeAngVel: Vec,
            timeStart: Real,
            timeEnd: Real,
            settings: ShapeCastSettings,
            filter: QueryFilter,
        ): ShapeCast?

        fun projectPointSimple(
            point: Vec,
            settings: PointProjectSettings,
            filter: QueryFilter,
        ): PointProject.Simple?

        fun projectPointComplex(
            point: Vec,
            settings: PointProjectSettings,
            filter: QueryFilter,
        ): PointProject.Complex?
    }

    val onCollision: EventDispatch<OnCollision>

    val onContactForce: EventDispatch<OnContactForce>

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
        val dt: Real,
        val totalMagnitude: Real,
        val colliderA: ColliderKey,
        val colliderB: ColliderKey,
        val manifolds: List<Manifold>,
    )
}
