package io.github.aecsocket.rattle

import java.util.function.Consumer

/**
 * A baked, physics-ready form of a [Geometry]. You can use this object as the shape of a [Collider], however
 * cannot get the original values of the [Geometry] back.
 *
 * If you need to access the original geometry, consider storing that alongside your shape.
 */
interface Shape : RefCounted

/**
 * Which rule is applied to both friction or restitution coefficients of bodies that have come into contact,
 * in order to determine the final coefficient.
 */
enum class CoeffCombineRule {
    AVERAGE,
    MIN,
    MULTIPLY,
    MAX,
}

/**
 * How a [Collider] behaves when other colliders come into contact with it.
 */
enum class PhysicsMode {
    /**
     * Will have a contact response, and apply forces to push other colliders away.
     */
    SOLID,

    /**
     * Wil have no contact response, and allow colliders to pass through this.
     */
    SENSOR,
}

/**
 * The default friction of a [PhysicsMaterial].
 */
const val DEFAULT_FRICTION: Real = 0.5

/**
 * The default restitution of a [PhysicsMaterial].
 */
const val DEFAULT_RESTITUTION: Real = 0.0

/**
 * A set of physical properties that can be applied to a [Collider].
 * @param friction A coefficient representing the static + dynamic friction, which is a force that opposes
 *                 motion between two bodies.
 *                 Minimum 0, typically between 0 and 1. 0 implies no friction, 1 implies very strong friction.
 * @param restitution A coefficient representing how elastic ("bouncy") a contact is.
 *                    Minimum 0, typically between 0 and 1.
 *                    0 implies that the exit velocity after a contact is 0,
 *                    1 implies that the exit velocity is the same as entry velocity.
 * @param frictionCombine Which rule is used to combine the friction coefficients of two colliding bodies.
 * @param restitutionCombine Which rule is used to combine the restitution coefficients of two colliding bodies.
 */
data class PhysicsMaterial(
    val friction: Real = DEFAULT_FRICTION,
    val restitution: Real = DEFAULT_RESTITUTION,
    val frictionCombine: CoeffCombineRule = CoeffCombineRule.AVERAGE,
    val restitutionCombine: CoeffCombineRule = CoeffCombineRule.AVERAGE,
) {
    init {
        require(friction >= 0) { "requires friction >= 0" }
        require(restitution >= 0) { "requires restitution >= 0" }
    }
}

/**
 * Mass properties of a [Collider], used in calculating forces and dynamics during the simulation.
 */
sealed interface Mass {
    /**
     * The mass properties will be calculated based on the shape's volume and the given [density].
     * - Mass is calculated
     * - Density is provided
     * - Inertia tensor is calculated
     * @param density The density of the shape. Must be greater than 0.
     */
    data class Density(val density: Real) : Mass {
        init {
            require(density > 0.0) { "requires density > 0.0" }
        }
    }

    /**
     * The mass properties will be calculated based on a fixed [mass].
     * - Mass is provided
     * - Density is calculated
     * - Inertia tensor is calculated
     * @param mass The mass of the shape. Must be greater than 0.
     */
    data class Constant(val mass: Real) : Mass {
        init {
            require(mass > 0.0) { "requires mass > 0.0" }
        }
    }
}

/**
 * A physical volume in space which can be collided with by other physics structures. This holds a shape
 * and physics properties, and may be attached to a [PhysicsSpace] to simulate it inside that space.
 * A collider may also be attached (parented) to a [RigidBody], which will make the collider determine its
 * position based on its parent body.
 *
 * This object may **not** be [destroy]'ed if it is attached to a [PhysicsSpace].
 *
 * To access the properties of this object, use the [read] and [write] methods to gain immutable and mutable
 * access respectively to the data. Do **not** store the [Access] objects, as they may be invalid later.
 */
interface Collider : Destroyable {
    /**
     * Gain immutable access to the properties of this object.
     *
     * Do **not** store the [Read] object, as it may be invalid later.
     */
    fun <R> read(block: (Read) -> R): R

    /**
     * Gain immutable access to the properties of this object.
     *
     * Do **not** store the [Read] object, as it may be invalid later.
     */
    fun read(block: Consumer<Read>) = read { block.accept(it) }

    /**
     * Gain mutable access to the properties of this object.
     *
     * Do **not** store the [Write] object, as it may be invalid later.
     */
    fun <R> write(block: (Write) -> R): R

    /**
     * Gain mutable access to the properties of this object.
     *
     * Do **not** store the [Write] object, as it may be invalid later.
     */
    fun write(block: Consumer<Write>) = write { block.accept(it) }

    /**
     * Provides immutable access to the properties of this object.
     */
    interface Access {
        /**
         * The underlying collider.
         */
        val handle: Collider

        /**
         * The shape.
         */
        val shape: Shape

        /**
         * The physics properties.
         */
        val material: PhysicsMaterial

        /**
         * The **absolute** position of the collider in the physics space, i.e. not relative to its parent body.
         */
        val position: Iso

        /**
         * The physics mode.
         */
        val physicsMode: PhysicsMode

        /**
         * The position of the collider **relative to its parent body**. Even if the collider has no parent, this will
         * keep the last set relative position.
         */
        val relativePosition: Iso

        /**
         * Which body this collider will follow.
         */
        val parent: RigidBody?

        /**
         * The world-space bounding box of this collider, determined by its shape and position.
         */
        fun bounds(): Aabb
    }

    /**
     * Provides immutable access to the properties of this object.
     */
    interface Read : Access

    /**
     * Provides mutable access to the properties of this object.
     */
    interface Write : Access {
        override var shape: Shape

        override var material: PhysicsMaterial

        override var position: Iso

        override var physicsMode: PhysicsMode

        override var relativePosition: Iso

        override var parent: RigidBody?
    }
}
