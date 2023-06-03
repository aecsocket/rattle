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
 * A single layer which a [Collider] may be placed on, via an [InteractionGroup].
 */
class InteractionLayer private constructor(val raw: Int) {
    companion object {
        /**
         * Creates a layer from a raw integer value. The integer have exactly one bit set.
         */
        fun fromRaw(raw: Int) = InteractionLayer(raw)
    }
}

private fun Array<out InteractionLayer>.bitMask() = fold(0) { acc, layer -> acc or layer.raw }

/**
 * A field in an [InteractionGroup], consisting of [InteractionLayer]s.
 */
data class InteractionField(val raw: Int) {
    companion object {
        /**
         * This field is enabled for all other layers.
         */
        val All = InteractionField(Int.MAX_VALUE)

        /**
         * This field is disabled for all other layers.
         */
        val None = InteractionField(0)
    }

    constructor(vararg layer: InteractionLayer) : this(layer.bitMask())

    /**
     * Create a new field with these additional layers.
     */
    fun with(vararg layer: InteractionLayer) = InteractionField(raw or layer.bitMask())

    /**
     * Create a new field with these layers removed.
     */
    fun without(vararg layer: InteractionLayer) = InteractionField(raw and (layer.bitMask().inv()))
}

/**
 * Specifies which [Collider]s a collider will collide with, by using bit masks of [InteractionLayer]s.
 * This object stores two [InteractionField]s:
 * - [memberships] - which layers this collider is a part of
 * - [filter] - which layers this collider may collide with
 *
 * If a collider collides with layer `X`, it means that the collider will collide with any others which have bit `X`
 * set in their [memberships] field.
 *
 * If we want to specify a collider which is part of the layers `[0, 2, 3]` and can collide with the layers `[2]`,
 * we specify the fields as:
 *
 * ```
 *       index  3 2 1 0
 * memberships  1 1 0 1
 *      filter  0 1 0 0
 * ```
 * which is written as
 * ```
 * InteractionGroup(
 *     memberships = 0b1101,
 *     filter = 0b0100,
 * )
 * ```
 *
 * For details on what this interaction group will do exactly, see [Collider].
 * @param memberships Which layers this collider is a part of.
 * @param filter Which layers this collider may collide with.
 */
data class InteractionGroup(
    val memberships: InteractionField,
    val filter: InteractionField,
) {
    companion object {
        /**
         * This group is part of all layers, and collides with all other layers.
         */
        val All = InteractionGroup(InteractionField.All, InteractionField.All)

        /**
         * This group is part of no layers, and collides with no other layers.
         */
        val None = InteractionGroup(InteractionField.None, InteractionField.None)
    }
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
 *
 * # Interaction groups
 *
 * A collider has two [InteractionGroup]s, which determine if a pair of colliders are considered in contact:
 * - [collisionGroup] - filters what pairs of colliders should have contacts generated.
 *   This happens after the broad-phase, at the beginning of the narrow-phase (see [PhysicsEngine.stepSpaces]).
 * - [solverGroup] - filters what pairs of colliders, with contacts already generated, should compute contact
 *   forces. This happens at the end of the narrow-phase.
 *
 * Typically, you would want to modify the [collisionGroup] to filter out contacts as early as possible; unless you
 * want contacts to be generated, but the *forces* to not be computed (maybe you want to modify them yourself later),
 * in which case you would use the [solverGroup].
 */
interface Collider {
    /**
     * The shape.
     */
    val shape: Shape

    /**
     * The physics properties.
     */
    val material: PhysicsMaterial

    val collisionGroup: InteractionGroup

    val solverGroup: InteractionGroup

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

    /**
     * Mutable interface for a [Collider].
     */
    interface Mut : Collider {
        override var shape: Shape

        override var material: PhysicsMaterial

        override var position: Iso

        override var physicsMode: PhysicsMode

        override var relativePosition: Iso
    }

    /**
     * Mutable owned interface for a [Collider].
     */
    interface Own : Mut, Destroyable
}
