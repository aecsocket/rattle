package io.github.aecsocket.rattle

import io.github.aecsocket.klam.*
import kotlin.math.max

typealias Real = Double
typealias Vec = DVec3
typealias Quat = DQuat
typealias Mat = DMat3
typealias Iso = DIso3
typealias Affine = DAffine3
typealias Aabb = DAabb3

/**
 * An object which is tied to a resource that must be manually destroyed after use.
 * You must only call the [destroy] method once; implementations may throw an exception
 * if a double-free is attempted.
 */
interface Destroyable {
    fun destroy()
}

/**
 * An object which is tied to a resource that is reference-counted. Each time an object
 * uses this resource, they [acquire] a reference (increment the [refCount]), and when that
 * object stops using it, they [release] the reference. Once the number of references reaches 0,
 * the object is destroyed.
 *
 * Objects may automatically acquire a reference if they are passed a RefCounted object, so you
 * will not have to do this yourself (until all objects which acquired a reference are now destroyed,
 * so your caller is the last object holding a reference). See the documentation for specific methods
 * for ref-counting behaviour.
 */
interface RefCounted {
    val refCount: Long

    fun acquire(): RefCounted

    fun release(): RefCounted
}

/**
 * The entry point to the physics system; an engine represents the manager of all physics resources and
 * structures. An engine is tied to a specific physics backend, and allows creating native objects to interface
 * with the physics backend.
 */
interface PhysicsEngine : Destroyable {
    /**
     * A human-readable, branded name for the physics backend.
     */
    val name: String

    /**
     * A human-readable version string for the physics backend. This is **not** required to follow any conventions
     * like SemVer.
     */
    val version: String

    /**
     * Creates a baked, physics-ready form of a [Geometry], which starts with a reference count of 1.
     * @param geom The geometry to bake.
     */
    fun createShape(geom: Geometry): Shape

    /**
     * Creates a [Collider] from the specified parameters, which by default is not attached to any [RigidBody].
     * @param shape The baked shape to use for this collider. The ref-count will be automatically incremented.
     * @param material The physical material properties of this collider.
     * @param position The absolute position of this collider in the world - **not** the position relative to the parent body.
     * @param mass The mass properties of this collider.
     * @param physics The physics interaction mode of this collider.
     */
    fun createCollider(
        shape: Shape,
        material: PhysicsMaterial,
        position: Iso = Iso(),
        mass: Mass = Mass.Density(1.0),
        physics: PhysicsMode = PhysicsMode.SOLID,
    ): Collider

    /**
     * Creates a non-moving from the specified parameters.
     * @param position The absolute position of this body in the world.
     */
    fun createFixedBody(
        position: Iso,
    ): FixedBody

    /**
     * Creates a movable body (dynamic or kinematic) from the specified parameters.
     * @param position The absolute position of this body in the world.
     * @param movingMode If this body is dynamic or kinematic (see [MovingMode]).
     * @param isCcdEnabled If continuous collision detection is enabled (see [MovingBody]).
     * @param linearVelocity The starting linear velocity, in m/s.
     * @param angularVelocity The starting angular velocity, in rad/s.
     * @param gravityScale The gravity multiplier for this body.
     * @param linearDamping The linear damping (see [MovingBody]).
     * @param angularDamping The angular damping (see [MovingBody]).
     * @param sleeping The sleep parameters (see [Sleeping]).
     */
    fun createMovingBody(
        position: Iso,
        movingMode: MovingMode = MovingMode.DYNAMIC,
        isCcdEnabled: Boolean = false,
        linearVelocity: Vec = Vec.Zero,
        angularVelocity: Vec = Vec.Zero,
        gravityScale: Real = 1.0,
        linearDamping: Real = DEFAULT_LINEAR_DAMPING,
        angularDamping: Real = DEFAULT_ANGULAR_DAMPING,
        sleeping: Sleeping = Sleeping.Enabled(false),
    ): MovingBody

    fun createImpulseJoint(axes: JointAxes): ImpulseJoint

    fun createMultibodyJoint(axes: JointAxes): MultibodyJoint

    /**
     * Creates an independent container for physics structures from the specified settings.
     */
    fun createSpace(settings: PhysicsSpace.Settings): PhysicsSpace

    /**
     * Simulates an update for a collection of spaces in parallel, with the given time step.
     *
     * Due to the implementation of physics spaces, a space can only be stepped a whole interval at once -
     * you cannot start a step, then wait for it to finish later (this would allow starting multiple steps for
     * different spaces at once, then waiting for all of them at the same time). However, through a specialized
     * native function, we *can* achieve the same effect if we provide all the spaces that we want to step upfront.
     *
     * @param dt The time step to simulate, in seconds.
     * @param spaces The spaces to step.
     */
    fun stepSpaces(dt: Real, spaces: Collection<PhysicsSpace>)
}

/**
 * Utility function to determine a number of threads from a config option.
 * @param raw The config option value passed by the user.
 * @param target The desired number of threads.
 */
fun numThreads(raw: Int, target: Int) = if (raw > 0) raw else {
    max(Runtime.getRuntime().availableProcessors() - 2, target)
}
