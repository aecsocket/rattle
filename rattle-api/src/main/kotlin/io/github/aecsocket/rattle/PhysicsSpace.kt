package io.github.aecsocket.rattle

import io.github.aecsocket.kbeam.EventDispatch
import io.github.aecsocket.klam.*
import java.util.concurrent.locks.ReentrantLock
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Required

/** A linear axis in 3D space. */
enum class LinAxis {
  X,
  Y,
  Z
}

/** A degree of freedom in 3D space. */
enum class Dof {
  X,
  Y,
  Z,
  ANG_X,
  ANG_Y,
  ANG_Z,
}

interface ContactManifold {}

/**
 * An independent object storing simulation data for a set of physics structures. This takes
 * ownership of objects like [RigidBody] and [Collider] instances, and allows manipulating and
 * querying the internal structures. An instance can be created through [PhysicsEngine.createSpace].
 */
interface PhysicsSpace : Destroyable {
  /**
   * Simulation parameters for a physics space.
   *
   * @param gravity The gravity **acceleration** applied to all bodies, in meters/sec.
   */
  @ConfigSerializable
  data class Settings(
      val gravity: DVec3 = DVec3(0.0, -9.81, 0.0),
  )

  /**
   * Settings for the simulation of the physics space. Note that not all options will be
   * configurable here, as some are considered implementation details. In this case, the settings
   * need to be configured with the actual implementation.
   */
  var settings: Settings

  /** The lock used for checking thread-safe access (see [PhysicsEngine]). */
  var lock: ReentrantLock?

  /** Provides access to the [Collider] instances in this space. */
  val colliders: ColliderContainer

  /** Provides access to the [RigidBody] instances in this space. */
  val rigidBodies: ActiveContainer<RigidBody, RigidBody.Mut, RigidBody.Own, RigidBodyKey>

  /** Provides access to the [ImpulseJoint] instances in this space. */
  val impulseJoints: ImpulseJointContainer

  /** Provides access to the [MultibodyJoint] instances in this space. */
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
    /** Attaches a [Collider] to a [RigidBody] (see [Collider]). */
    fun attach(coll: ColliderKey, to: RigidBodyKey)

    /** Detaches a [Collider] from any parent [RigidBody] (see [Collider]). */
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

  /** Provides access to performing queries on this physics space, such as raycasts. */
  val query: PhysicsQuery

  /**
   * Runs when a collision between two [Collider]s is either started or stopped. Runs only when
   * [ColliderEvent.COLLISION] is marked on at least one of the colliders.
   */
  val onCollision: EventDispatch<OnCollision>

  val onContactForce: EventDispatch<OnContactForce>

  val onFilterContactPair: EventDispatch<OnFilterContactPair>

  val onFilterIntersectionPair: EventDispatch<OnFilterIntersectionPair>

  val onModifySolverContacts: EventDispatch<OnModifySolverContacts>

  data class OnCollision(
      val state: State,
      val colliderA: ColliderKey,
      val colliderB: ColliderKey,
      val manifolds: List<ContactManifold>,
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
      val manifolds: List<ContactManifold>,
  )

  data class OnFilterContactPair(
      val colliderA: ColliderKey,
      val colliderB: ColliderKey,
      val bodyA: RigidBodyKey?,
      val bodyB: RigidBodyKey?,
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
      val bodyA: RigidBodyKey?,
      val bodyB: RigidBodyKey?,
      var createPair: Boolean = true,
  )

  data class OnModifySolverContacts(
      val colliderA: ColliderKey,
      val colliderB: ColliderKey,
      val bodyA: RigidBodyKey?,
      val bodyB: RigidBodyKey?,
      val manifold: ContactManifold,
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
      val flags: Int = 0,
      val group: InteractionGroup = InteractionGroup.all,
      val excludeCollider: ColliderKey? = null,
      val excludeRigidBody: RigidBodyKey? = null,
      val predicate: ((Collider, ColliderKey) -> Result)? = null,
  )

  @ConfigSerializable
  data class RayCastSettings(
      @Required val isSolid: Boolean,
  )

  @ConfigSerializable
  data class ShapeCastSettings(
      @Required val stopAtPenetration: Boolean,
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

  /**
   * Gets every collider whose broad-phase bounding box ([Collider.bounds]) intersects with
   * [bounds], in no specific ordering.
   *
   * This is the fastest and cheapest query, as it only reads the broad-phase. Use this if you only
   * need a coarse set of colliders in a general area.
   *
   * @param bounds The axis-aligned bounds to test against.
   * @param fn The callback to run for each result, allowing you to continue or stop the query.
   */
  fun intersectBounds(
      bounds: DAabb3,
      fn: (ColliderKey) -> Result,
  )

  /**
   * Gets every collider whose broad-phase bounding box ([Collider.bounds]) intersects with
   * [bounds], in no specific ordering.
   *
   * This is the fastest and cheapest query, as it only reads the broad-phase. Use this if you only
   * need a coarse set of colliders in a general area.
   *
   * @param bounds The axis-aligned bounds to test against.
   * @return The first result found, stopping the query after this result.
   */
  fun intersectBoundsFirst(bounds: DAabb3): ColliderKey? {
    var res: ColliderKey? = null
    intersectBounds(bounds) {
      res = it
      Result.STOP
    }
    return res
  }

  /**
   * Gets every collider whose broad-phase bounding box ([Collider.bounds]) intersects with
   * [bounds], in no specific ordering.
   *
   * This is the fastest and cheapest query, as it only reads the broad-phase. Use this if you only
   * need a coarse set of colliders in a general area.
   *
   * @param bounds The axis-aligned bounds to test against.
   * @return All collected results.
   */
  fun intersectBoundsAll(
      bounds: DAabb3,
  ): List<ColliderKey> {
    val res = ArrayList<ColliderKey>()
    intersectBounds(bounds) {
      res += it
      Result.CONTINUE
    }
    return res
  }

  /**
   * Gets every collider which contains [point] inside of its shape.
   *
   * @param point The point to test against.
   * @param filter The filter for determining which colliders are tested against.
   * @param fn The callback to run for each result, allowing you to continue or stop the query.
   */
  fun intersectPoint(
      point: DVec3,
      filter: Filter,
      fn: (ColliderKey) -> Result,
  )

  /**
   * Gets every collider which contains [point] inside of its shape.
   *
   * @param point The point to test against.
   * @param filter The filter for determining which colliders are tested against.
   * @return The first result found, stopping the query after this result.
   */
  fun intersectPointFirst(
      point: DVec3,
      filter: Filter,
  ): ColliderKey? {
    var res: ColliderKey? = null
    intersectPoint(point, filter) {
      res = it
      Result.STOP
    }
    return res
  }

  /**
   * Gets every collider which contains [point] inside of its shape.
   *
   * @param point The point to test against.
   * @param filter The filter for determining which colliders are tested against.
   * @return All collected results.
   */
  fun intersectPointAll(
      point: DVec3,
      filter: Filter,
  ): List<ColliderKey> {
    val res = ArrayList<ColliderKey>()
    intersectPoint(point, filter) {
      res += it
      Result.CONTINUE
    }
    return res
  }

  /**
   * Gets every collider whose shape intersects [shape] if that shape was positioned at [shapePos].
   *
   * @param shape The shape to test against.
   * @param shapePos The position of the tested shape in the world.
   * @param filter The filter for determining which colliders are tested against.
   * @param fn The callback to run for each result, allowing you to continue or stop the query.
   */
  fun intersectShape(
      shape: Shape,
      shapePos: DIso3,
      filter: Filter,
      fn: (ColliderKey) -> Result,
  )

  /**
   * Gets every collider whose shape intersects [shape] if that shape was positioned at [shapePos].
   *
   * @param shape The shape to test against.
   * @param shapePos The position of the tested shape in the world.
   * @param filter The filter for determining which colliders are tested against.
   * @return The first result found, stopping the query after this result.
   */
  fun intersectShapeFirst(
      shape: Shape,
      shapePos: DIso3,
      filter: Filter,
  ): ColliderKey? {
    var res: ColliderKey? = null
    intersectShape(shape, shapePos, filter) {
      res = it
      Result.STOP
    }
    return res
  }

  /**
   * Gets every collider whose shape intersects [shape] if that shape was positioned at [shapePos].
   *
   * @param shape The shape to test against.
   * @param shapePos The position of the tested shape in the world.
   * @param filter The filter for determining which colliders are tested against.
   * @return All collected results.
   */
  fun intersectShapeAll(
      shape: Shape,
      shapePos: DIso3,
      filter: Filter,
  ): List<ColliderKey> {
    val res = ArrayList<ColliderKey>()
    intersectShape(shape, shapePos, filter) {
      res += it
      Result.CONTINUE
    }
    return res
  }

  /**
   * Gets every collider whose shape intersects [ray], up to a maximum distance of [maxDistance]
   * away from [DRay3.origin].
   *
   * @param ray The ray to test against.
   * @param maxDistance The maximum distance the ray will hit colliders.
   * @param settings Settings on how the raycast will behave.
   * @param filter The filter for determining which colliders are tested against.
   * @param fn The callback to run for each result, allowing you to continue or stop the query.
   */
  fun castRay(
      ray: DRay3,
      maxDistance: Double,
      settings: RayCastSettings,
      filter: Filter,
      fn: (RayCast) -> Result,
  )

  /**
   * Gets every collider whose shape intersects [ray], up to a maximum distance of [maxDistance]
   * away from [DRay3.origin].
   *
   * @param ray The ray to test against.
   * @param maxDistance The maximum distance the ray will hit colliders.
   * @param settings Settings on how the raycast will behave.
   * @param filter The filter for determining which colliders are tested against.
   * @return The first result found, stopping the query after this result.
   */
  fun castRayFirst(
      ray: DRay3,
      maxDistance: Double,
      settings: RayCastSettings,
      filter: Filter,
  ): RayCast? {
    var res: RayCast? = null
    castRay(ray, maxDistance, settings, filter) {
      res = it
      Result.STOP
    }
    return res
  }

  /**
   * Gets every collider whose shape intersects [ray], up to a maximum distance of [maxDistance]
   * away from [DRay3.origin].
   *
   * @param ray The ray to test against.
   * @param maxDistance The maximum distance the ray will hit colliders.
   * @param settings Settings on how the raycast will behave.
   * @param filter The filter for determining which colliders are tested against.
   * @return All collected results.
   */
  fun castRayAll(
      ray: DRay3,
      maxDistance: Double,
      settings: RayCastSettings,
      filter: Filter,
  ): List<RayCast> {
    val res = ArrayList<RayCast>()
    castRay(ray, maxDistance, settings, filter) {
      res += it
      Result.CONTINUE
    }
    return res
  }

  /**
   * Gets the first collider whose shape intersects a swept version of the provided shape. The swept
   * [shape] starts at [shapePos] and travels in [shapeDir] up to a distance of [maxDistance].
   *
   * This is the cheapest shape cast method, which does not take rotational velocity into account.
   *
   * @param shape The shape to cast.
   * @param shapePos The position the shape starts at.
   * @param shapeDir The direction which the shape moves in.
   * @param maxDistance How far the shape will move in the [shapeDir] direction.
   * @param settings Settings on how the shape cast will behave.
   * @param filter The filter for determining which colliders are tested against.
   */
  fun castShape(
      shape: Shape,
      shapePos: DIso3,
      shapeDir: DVec3,
      maxDistance: Double,
      settings: ShapeCastSettings,
      filter: Filter,
  ): ShapeCast?

  /**
   * Gets the first collider whose shape intersects a swept version of the provided shape. The swept
   * [shape] starts at [shapePos] and travels:
   * - linearly by [shapeLinVel]
   * - rotationally by [shapeAngVel] around the local center [shapeLocalCenter]
   *
   * The sweep starts at [timeStart] and ends at [timeEnd].
   *
   * This is more expensive than [castShape] as it also takes rotational velocity into account.
   *
   * @param shape The shape to cast.
   * @param shapePos The position the shape starts at.
   * @param shapeLocalCenter The local-space point at which the rotational part of motion is
   *   applied.
   * @param shapeLinVel The translational velocity of this shape motion.
   * @param shapeAngVel The rotational velocity of this shape motion.
   * @param timeStart The starting time of the shape cast motion.
   * @param timeEnd The ending time of the shape cast motion.
   * @param settings Settings on how the shape cast will behave.
   * @param filter The filter for determining which colliders are tested against.
   */
  fun castShapeNonLinear(
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
   *
   * @param point The point in world-space to project.
   * @param filter The filter for determining which colliders are tested against.
   */
  fun projectPoint(
      point: DVec3,
      filter: Filter,
  ): PointProject?
}
