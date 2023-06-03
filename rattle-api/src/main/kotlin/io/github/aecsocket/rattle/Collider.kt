package io.github.aecsocket.rattle

/**
 * A baked, physics-ready form of a [Geometry]. You can use this object as the shape of a [Collider], however
 * cannot get the original values of the [Geometry] back.
 *
 * If you need to access the original geometry, consider storing that alongside your shape.
 *
 * # Rapier implementation
 *
 * Internally, Rapier uses Rust `Arc` structures for atomic reference-counting of shapes. This Shape class
 * is a wrapper around a single individual instance of `Arc`, *not* the data stored by the `Arc`. However,
 * the equality and hash-code operations for the shape **are** based on the underlying data stored, rather than
 * the individual reference-counting object. Therefore, it is safe to use a Shape as a key, and test for reference
 * equality with them, **only if** the underlying shape is not freed ([Destroyable.destroy]'ed) yet (otherwise you would be
 * accessing a freed `Arc`, with an undefined data pointer).
 */
interface Shape : RefCounted {
    override fun acquire(): Shape

    override fun release(): Shape
}

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
     * The mass properties will be calculated based on the shape's volume and the given [density], with a default of 1.
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
     * The mass properties will be calculated based on a fixed [mass], in kilograms.
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
 * A key used to index into a [PhysicsSpace] to gain a reference, mutable or immutable, to a [Collider].
 */
interface ColliderKey

/**
 * A physical volume in space which can be collided with by other physics structures. This holds a shape
 * and physics properties, and may be attached to a [PhysicsSpace] to simulate it inside that space.
 * A collider may also be attached (parented) to a [RigidBody], which will make the collider determine its
 * position based on its parent body.
 */
object Collider {
    /**
     * Immutable interface for a [Collider].
     */
    interface Read {
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
         * The handle of which body this collider will follow (see [Collider]).
         */
        val parent: RigidBodyKey?

        /**
         * The world-space bounding box of this collider, determined by its shape and position.
         */
        fun bounds(): Aabb
    }

    /**
     * Mutable interface for a [Collider].
     */
    interface Write : Read {
        override var shape: Shape

        override var material: PhysicsMaterial

        override var position: Iso

        override var physicsMode: PhysicsMode

        override var relativePosition: Iso
    }

    /**
     * Mutable owned interface for a [Collider].
     */
    interface Own : Write, Destroyable
}
