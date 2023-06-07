package io.github.aecsocket.rattle

/**
 * The default multiplier to slow a [RigidBody]'s linear velocity down by on every physics step.
 */
const val DEFAULT_LINEAR_DAMPING: Real = 0.05

/**
 * The default multiplier to slow a [RigidBody]'s angular velocity down by on every physics step.
 */
const val DEFAULT_ANGULAR_DAMPING: Real = 0.05

/**
 * How the dynamics of a body are simulated. See [RigidBody] for details.
 */
enum class RigidBodyType {
    /**
     * This body does not move.
     */
    FIXED,

    /**
     * The body's forces and impulses are calculated by the physics engine.
     */
    DYNAMIC,

    /**
     * The body's target position is manually determined by the user.
     */
    KINEMATIC,
}

/**
 * A key used to index into a [PhysicsSpace] to gain a reference, mutable or immutable, to a [RigidBody].
 */
interface RigidBodyKey

/**
 * A physics structure which simulates dynamics - velocity, forces, friction, etc. - when attached to a [PhysicsSpace].
 * A body may have [Collider]s attached to it, which provide the physical collision shape used in contact response.
 *
 * # Body type
 *
 * A body may have one of several [RigidBodyType]s, which can change after construction:
 * - [RigidBodyType.FIXED] - the body is not moved (position or velocity) by the physics engine
 * - [RigidBodyType.DYNAMIC] - the body's position and velocity is managed by the physics engine, so it can be pushed
 *   by other colliders.
 * - [RigidBodyType.KINEMATIC] - the body's position and velocity is managed by the physics engine, but the user
 *   manually sets a desired target position via [Mut.kinematicMoveTo], and the engine calculates the required
 *   velocity to move it there.
 *
 * Note that not all operations can be applied to all body types - for example, [Mut.applyForce] on a
 * [RigidBodyType.FIXED] does not make sense. In these cases, the API will simply fail silently, rather than
 * throw an exception. It is your responsibility to make sure your bodies are of the correct type before you
 * call methods on them.
 *
 * # Continuous collision detection (CCD)
 *
 * Since a [PhysicsSpace] uses a discrete time-stepping approach, collisions may get missed if a body is moving too
 * fast. This is known as "tunneling", and it allows a body to effectively move through another body without any
 * collisions being detected. In this case, setting the fast-moving body to have CCD enabled (using [Mut.isCcdEnabled])
 * will allow it to detect collisions via a "sweep" between its old and new position, and find collisions in between.
 * If there is a collision, it will be teleported back to just before it collides that body, effectively preventing
 * tunneling. This works for both the linear and angular velocities.
 *
 * To check if a body is currently being simulated with CCD, use [isCcdActive].
 *
 * # Sleeping
 *
 * A body may not always be moving, so it would be a waste of resources to simulate its movement if it has not
 * moved much in the past few physics steps. This process is called sleeping, and a body is automatically woken up
 * if another body collides with it, or if woken up by the user.
 *
 * # Damping
 *
 * To simulate phenomena like air resistance, it would be too computationally expensive to actually simulate fluid
 * dynamics. Instead, we use a simple factor which the body's velocities are multiplied by on every time step. This
 * effectively slows the body down more the faster that it moves. There are two damping factors:
 * - [linearDamping] - the multiplier for the linear velocity (default [DEFAULT_LINEAR_DAMPING]).
 * - [angularDamping] - the multiplier for the angular velocity (default [DEFAULT_ANGULAR_DAMPING]).
 */
interface RigidBody {
    /*
            linearVelocity: Vec = Vec.Zero,
        angularVelocity: Vec = Vec.Zero,
        isCcdEnabled: Boolean = false,
        gravityScale: Real = 1.0,
        linearDamping: Real = DEFAULT_LINEAR_DAMPING,
        angularDamping: Real = DEFAULT_ANGULAR_DAMPING,
        sleeping: Sleeping = Sleeping.Enabled(false),
     */

    /**
     * The body type (see [RigidBody]).
     */
    val type: RigidBodyType

    /**
     * The handles to colliders attached to this body.
     */
    val colliders: Collection<ColliderKey>

    // TODO: how does center of mass work??
    /**
     * The absolute position of this body in the world.
     */
    val position: Iso

    /**
     * How fast and in what direction the body is moving linearly, in meters/second.
     */
    val linearVelocity: Vec

    /**
     * How fast and in what direction the body is moving around its angular axes, in radians/second.
     * This is represented as an axis-angle vector, where the magnitude is the angle.
     */
    val angularVelocity: Vec

    /**
     * If this body has CCD enabled (see [RigidBody]).
     */
    val isCcdEnabled: Boolean

    /**
     * If this body is currently being simulated with CCD (see [RigidBody]).
     */
    val isCcdActive: Boolean

    /**
     * The factor by which gravity is applied to this body. Default of 1.
     */
    val gravityScale: Real

    /**
     * The linear damping factor (see [RigidBody]).
     */
    val linearDamping: Real

    /**
     * The angular damping factor (see [RigidBody]).
     */
    val angularDamping: Real

    /**
     * If this body is currently sleeping (see [RigidBody]).
     */
    val isSleeping: Boolean

    /**
     * How much force has been applied by the user in this simulation step.
     */
    val appliedForce: Vec

    /**
     * How much torque has been applied by the user in this simulation step.
     */
    val appliedTorque: Vec

    /**
     * Calculates the kinetic energy of this body.
     */
    fun kineticEnergy(): Real

    /**
     * Mutable interface for a [RigidBody].
     */
    interface Mut : RigidBody {
        fun type(value: RigidBodyType): Mut

        // TODO center of mass
        /**
         * Sets the absolute position of this body in the world.
         *
         * **Note:** this is not a physically accurate action, as the body will effectively teleport from one position
         * to another without considering collisions in between. If you want to have fine control over where the body
         * moves, but have it still consider collisions in between, consider using a [RigidBodyType.KINEMATIC] and
         * [kinematicMoveTo].
         */
        fun position(value: Iso): Mut

        fun linearVelocity(value: Vec): Mut

        fun angularVelocity(value: Vec): Mut

        fun isCcdEnabled(value: Boolean): Mut

        fun gravityScale(value: Real): Mut

        fun linearDamping(value: Real): Mut

        fun angularDamping(value: Real): Mut

        fun canSleep(value: Boolean): Mut

        /**
         * For [RigidBodyType.DYNAMIC]: applies a (linear) force in Newtons at the center of the body.
         */
        fun applyForce(force: Vec)

        /**
         * For [RigidBodyType.DYNAMIC]: applies a (linear) force in Newtons at a specific position on the body,
         * taking into account rotation.
         */
        fun applyForceAt(force: Vec, at: Vec)

        /**
         * For [RigidBodyType.DYNAMIC]: applies a (linear) impulse - instantaneous change in force - at the center of
         * the body.
         */
        fun applyImpulse(impulse: Vec)

        /**
         * For [RigidBodyType.DYNAMIC]: applies a (linear) impulse - instantaneous change in force - at a specific
         * position on the body, taking into account rotation.
         */
        fun applyImpulseAt(impulse: Vec, at: Vec)

        /**
         * For [RigidBodyType.DYNAMIC]: applies an (angular) torque, causing a change in rotation.
         */
        fun applyTorque(torque: Vec)

        /**
         * For [RigidBodyType.DYNAMIC]: applies an (angular) torque impulse - instantaneous change in torque - causing
         * a change in rotation.
         */
        fun applyTorqueImpulse(torqueImpulse: Vec)

        /**
         * For [RigidBodyType.KINEMATIC]: sets this body's kinematic target to a specific position in the world, and
         * the physics engine will apply the correct velocities to move it to that position.
         * In this way, the movement is physically accurate (pushing other physics objects out of the way) but this
         * body will not be pushed by others.
         */
        fun kinematicMoveTo(to: Iso)

        /**
         * Puts this body to sleep (see [RigidBody]).
         */
        fun sleep()

        /**
         * Wakes this body up if it is sleeping (see [RigidBody]).
         * @param strong If true, this body will be guaranteed to stay awake for at least a few physics steps.
         *               Otherwise, it might immediately go back to sleep if it still isn't moving enough.
         */
        fun wakeUp(strong: Boolean)
    }

    /**
     * Mutable owned interface for a [RigidBody].
     */
    interface Own : Mut, Destroyable {
        override fun type(value: RigidBodyType): Own

        override fun position(value: Iso): Own

        override fun linearVelocity(value: Vec): Own

        override fun angularVelocity(value: Vec): Own

        override fun isCcdEnabled(value: Boolean): Own

        override fun gravityScale(value: Real): Own

        override fun linearDamping(value: Real): Own

        override fun angularDamping(value: Real): Own

        override fun canSleep(value: Boolean): Own
    }
}
