package io.github.aecsocket.rattle

import io.github.aecsocket.klam.*
import kotlin.math.max
import kotlin.math.min

/**
 * An object which is tied to a resource that must be manually destroyed after use. You must only
 * call the [destroy] method once; implementations may throw an exception if a double-free is
 * attempted.
 */
interface Destroyable {
  fun destroy()
}

/**
 * An object which is tied to a resource that is reference-counted. Each time an object uses this
 * resource, they [acquire] a reference (increment the [refCount]), and when that object stops using
 * it, they [release] the reference. Once the number of references reaches 0, the object is
 * destroyed.
 *
 * Any functions or classes which accept a [RefCounted] will **not** automatically [acquire] a
 * reference; this is your responsibility as the caller, but allows you a fine level of control over
 * what objects own what references.
 */
interface RefCounted {
  val refCount: Long

  /** Adds one to the reference count of this object. */
  fun acquire(): RefCounted

  /**
   * Removes one from the reference count of this object. If the object's reference count drops to
   * 0, it will be freed and be unusable.
   */
  fun release(): RefCounted
}

/**
 * The entry point to the physics system; an engine represents the manager of all physics resources
 * and structures. An engine is tied to a specific physics backend, and allows creating native
 * objects to interface with the physics backend.
 *
 * The units used throughout the engine are metric: **meters, radians, seconds, kilograms**.
 *
 * # Thread safety
 *
 * Physics structures used by the engine are not thread-safe, so must be treated carefully when
 * interacting with them from multiple threads. Typically, the user of e.g. a [PhysicsSpace] or
 * [RigidBody] would wrap accesses to that object in a lock. To enforce this, you can specify a
 * [java.util.concurrent.locks.ReentrantLock] on some structures. Before any read or write access,
 * this lock will be checked to make sure the current thread holds an exclusive lock. If it does
 * not, an exception will be thrown.
 */
interface PhysicsEngine : Destroyable {
  /** A human-readable, branded name for the physics backend. */
  val name: String

  /**
   * A human-readable version string for the physics backend. This is **not** required to follow any
   * conventions like SemVer.
   */
  val version: String

  /**
   * Creates a baked, physics-ready form of a [Geometry], which starts with a reference count of 1.
   * See [RefCounted] for information on using ref-counted objects.
   *
   * @param geom The geometry to bake.
   */
  fun createShape(geom: Geometry): Shape

  /**
   * Creates a [Collider] with default parameters, which is not attached to any [RigidBody] or
   * [PhysicsSpace].
   *
   * @param shape The baked shape to use for this collider.
   * @param position The starting position for this collider (see [Collider.Start]).
   */
  fun createCollider(shape: Shape, position: Collider.Start): Collider.Own

  /**
   * Creates a [RigidBody] with default parameters, which is not attached to any [PhysicsSpace].
   *
   * @param type The dynamics type of this body.
   * @param position The absolute position of this body in the world.
   */
  fun createBody(type: RigidBodyType, position: DIso3): RigidBody.Own

  /**
   * Creates a [Joint] with default parameters, which is not attached to any [RigidBody] or
   * [PhysicsSpace].
   */
  fun createJoint(): Joint

  /** Creates an independent container for physics structures from the specified settings. */
  fun createSpace(settings: PhysicsSpace.Settings = PhysicsSpace.Settings()): PhysicsSpace

  /**
   * Simulates an update for a collection of spaces in parallel, with the given time step.
   *
   * Due to the implementation of physics spaces, a space can only be stepped a whole interval at
   * once - you cannot start a step, then wait for it to finish later (this would allow starting
   * multiple steps for different spaces at once, then waiting for all of them at the same time).
   * However, through a specialized native function, we *can* achieve the same effect if we provide
   * all the spaces that we want to step upfront.
   *
   * # Stages
   *
   * The update step of a physics engine is typically split into:
   * - Broad-phase - collision pairs are generated between all [Collider]s, using a bounding volume
   *   hierarchy to accelerate these queries. These pairs are coarse, and do not guarantee that two
   *   bodies did actually collide.
   * - Narrow-phase - all collision pairs previously generated are checked to see if they actually
   *   collided, and if so, compute the contacts and forces necessary in order to resolve them.
   *
   * @param dt The time step to simulate, in seconds.
   * @param spaces The spaces to step.
   */
  fun stepSpaces(dt: Double, spaces: Collection<PhysicsSpace>)

  /**
   * Utility class for building a [PhysicsEngine], allowing defining properties required for the
   * engine.
   */
  interface Builder {
    /**
     * Creates and registers a new [InteractionLayer], returning a reference to it, which can be
     * used to read and modify [InteractionField]s.
     */
    fun registerInteractionLayer(): InteractionLayer

    /** Builds the engine with the specified parameters. */
    fun build(): PhysicsEngine
  }
}

/**
 * Utility function to determine a number of threads from a config option.
 *
 * @param raw The config option value passed by the user.
 * @param target The desired number of threads.
 */
fun numThreads(raw: Int, target: Int) =
    if (raw > 0) raw
    else {
      max(Runtime.getRuntime().availableProcessors() - 2, min(1, target))
    }
