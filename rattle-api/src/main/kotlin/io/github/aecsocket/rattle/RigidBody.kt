package io.github.aecsocket.rattle

import io.github.aecsocket.rattle.Collider.Access
import io.github.aecsocket.rattle.Collider.Read
import java.util.function.Consumer

/**
 * The default multiplier to slow a [RigidBody]'s linear velocity down by on every physics step.
 */
const val DEFAULT_LINEAR_DAMPING: Real = 0.05

/**
 * The default multiplier to slow a [RigidBody]'s angular velocity down by on every physics step.
 */
const val DEFAULT_ANGULAR_DAMPING: Real = 0.05

/**
 * How this [MovingBody] behaves when its dynamics are simulated.
 */
enum class MovingMode {
    /**
     * The body's forces and impulses are calculated by the physics engine - it is pushed by other colliders.
     */
    DYNAMIC,

    /**
     * The body's target position is manually determined by the user, and the physics engine applies the correct
     * velocity to move it to that position. The body can **not** be pushed by other colliders.
     */
    KINEMATIC
}

/**
 * Properties around a [RigidBody]'s ability to go to sleep (not be simulated in order to save
 * resources). See [MovingBody] for details.
 */
sealed interface Sleeping {
    /**
     * This body is not allowed to go to sleep automatically.
     */
    /* TODO Kotlin 1.9: data */ object Disabled : Sleeping

    /**
     * This body is allowed to go to sleep automatically.
     * @param sleeping The initial sleep state of the body.
     */
    data class Enabled(val sleeping: Boolean) : Sleeping
}

/**
 * A physics structure which simulates dynamics - velocity, forces, friction, etc. - when attached to a [PhysicsSpace].
 * A body may have [Collider]s attached to it, which provide the physical collision shape used in contact response.
 * [MovingBody] instances also have extra physics information; see the documentation for that class for details.
 *
 * This object may **not** be [destroy]'ed if it is attached to a [PhysicsSpace].
 *
 * To access the properties of this object, use the `read*` and `write*` methods to gain immutable and mutable
 * access respectively to the data. Do **not** store the [Access] objects, as they may be invalid later.
 * - To read body properties common to all types, use [readBody].
 * - To read body properties for a specialized body type, see the documentation of that body subclass.
 */
interface RigidBody : Destroyable {
    /**
     * Gain immutable access to the common properties of this object.
     *
     * Do **not** store the [Read] object, as it may be invalid later.
     */
    fun <R> readBody(block: (Read) -> R): R

    /**
     * Gain immutable access to the common properties of this object.
     *
     * Do **not** store the [Read] object, as it may be invalid later.
     */
    fun readBody(block: Consumer<Read>) = readBody { block.accept(it) }

    /**
     * Gain mutable access to the common properties of this object.
     *
     * Do **not** store the [Write] object, as it may be invalid later.
     */
    fun <R> writeBody(block: (Write) -> R): R

    /**
     * Gain mutable access to the common properties of this object.
     *
     * Do **not** store the [Write] object, as it may be invalid later.
     */
    fun writeBody(block: Consumer<Write>) = writeBody { block.accept(it) }

    /**
     * Provides immutable access to the properties of this object.
     */
    interface Access {
        /**
         * The underlying body.
         */
        val handle: RigidBody

        // TODO: how does center of mass work??
        /**
         * The absolute position of this body in the world.
         */
        val position: Iso

        /**
         * The colliders attached to this body.
         */
        val colliders: Collection<Collider>

        /**
         * If this body is currently sleeping.
         */
        val isSleeping: Boolean
    }

    /**
     * Provides immutable access to the properties of this object.
     */
    interface Read : Access

    /**
     * Provides mutable access to the properties of this object.
     */
    interface Write : Access {
        // TODO center of mass
        /**
         * Sets the absolute position of this body in the world.
         */
        override var position: Iso

        /**
         * Puts this body to sleep. See [Sleeping].
         */
        fun sleep()

        /**
         * Wakes this body up if it is sleeping. See [Sleeping].
         * @param strong If true, this body will be guaranteed to stay awake for at least a few physics steps.
         *               Otherwise, it might immediately go back to sleep if it still isn't moving enough.
         */
        fun wakeUp(strong: Boolean)
    }
}

/**
 * A [RigidBody] which is unable to move. Consider just using a [Collider]
 */
interface FixedBody : RigidBody {
    fun <R> readFixed(block: (Read) -> R): R

    fun <R> writeFixed(block: (Write) -> R): R

    interface Access : RigidBody.Access {
        override val handle: FixedBody
    }

    interface Read : Access, RigidBody.Read

    interface Write : Access, RigidBody.Write
}

/**
 *
 *
 * # Sleeping
 *
 * A body may not always be moving, so it would be a waste of resources to simulate its movement if it has not
 * moved much in the past few physics steps. This process is called sleeping, and a body is automatically woken up
 * if another body collides with it, or if woken up by the user.
 */
interface MovingBody : RigidBody {
    fun <R> readMoving(block: (Read) -> R): R

    fun readMoving(block: Consumer<Read>) = readMoving { block.accept(it) }

    fun <R> writeMoving(block: (Write) -> R): R

    fun writeMoving(block: Consumer<Write>) = writeMoving { block.accept(it) }

    interface Access : RigidBody.Access {
        override val handle: MovingBody

        val movingMode: MovingMode

        val isCcdEnabled: Boolean

        val linearVelocity: Vec

        val angularVelocity: Vec

        val gravityScale: Real

        val linearDamping: Real

        val angularDamping: Real

        val kineticEnergy: Real

        val appliedForce: Vec

        val appliedTorque: Vec
    }

    interface Read : Access, RigidBody.Read

    interface Write : Access, RigidBody.Write {
        override var movingMode: MovingMode

        override var isCcdEnabled: Boolean

        override var linearVelocity: Vec

        override var angularVelocity: Vec

        override var gravityScale: Real

        override var linearDamping: Real

        override var angularDamping: Real

        fun applyForce(force: Vec)

        fun applyForceAt(force: Vec, at: Vec)

        fun applyImpulse(impulse: Vec)

        fun applyImpulseAt(impulse: Vec, at: Vec)

        fun applyTorque(torque: Vec)

        fun applyTorqueImpulse(torqueImpulse: Vec)

        fun kinematicTarget(position: Iso)
    }
}
