package io.github.aecsocket.rattle

import org.spongepowered.configurate.objectmapping.ConfigSerializable

/**
 * A raw, serializable form of a physical volume that can be constructed by a user. You can **not** use this object as
 * the shape of a [Collider], however you can convert it into a [Shape] using [PhysicsEngine.createShape]. This object
 * stores no information on world-space position; all parameters are given in local-space.
 *
 * Not every geometry has the same performance and stability. You should use the simplest shape that fits your
 * use case, rather than trying to make the physics shape match the visible shape.
 */
sealed interface Geometry

/**
 * A geometry which is guaranteed to be always convex. This is the type you should prefer working with, as they
 * are the cheapest and simplest to work with.
 */
sealed interface ConvexGeometry : Geometry

/**
 * A ball centered around zero with a defined radius.
 * @param radius The radius of the shape. Must be greater than 0.
 */
@ConfigSerializable
data class Sphere(
    val radius: Real,
) : ConvexGeometry {
    init {
        require(radius > 0.0) { "requires radius > 0.0" }
    }
}

/**
 * A cuboid centered around zero defined by its half-extent.
 * @param halfExtent The half-lengths of the box. Components must all be greater than 0.
 */
@ConfigSerializable
data class Box(
    val halfExtent: Vec,
) : ConvexGeometry {
    init {
        require(halfExtent.x > 0.0) { "requires halfExtent.x > 0.0" }
        require(halfExtent.y > 0.0) { "requires halfExtent.y > 0.0" }
        require(halfExtent.z > 0.0) { "requires halfExtent.z > 0.0" }
    }
}

/**
 * A "swept sphere" shape centered around zero defined by an axis, half-height and radius.
 * @param axis The axis in which the half-height stretches.
 * @param halfHeight The half-height of the capsule, up to the start of the hemisphere "caps" of the capsule.
 *                   If this were 0 (which is invalid), the shape would be a sphere defined by [radius].
 *                   Must be greater than 0.
 * @param radius The radius of the hemisphere caps of the capsule. Must be greater than 0.
 */
@ConfigSerializable
data class Capsule(
    val axis: LinAxis,
    val halfHeight: Real,
    val radius: Real,
) : ConvexGeometry {
    init {
        require(halfHeight > 0.0) { "requires halfHeight > 0.0" }
        require(radius > 0.0) { "requires radius > 0.0" }
    }
}

/**
 * A cylinder shape centered around zero, with its length along the **Y** axis, defined by a half-height and radius.
 *
 * **Note:** the axis that the cylinder rests on cannot be defined here. Instead, transform the collider that the
 * shape is placed on.
 * @param halfHeight The half-height of the cylinder. Must be greater than 0.
 * @param radius The radius of the cylinder. Must be greater than 0.
 */
@ConfigSerializable
data class Cylinder(
    val halfHeight: Real,
    val radius: Real,
) : ConvexGeometry {
    init {
        require(halfHeight > 0.0) { "requires halfHeight > 0.0" }
        require(radius > 0.0) { "requires radius > 0.0" }
    }
}

/**
 * A cone shape centered around zero, with its length along the **Y** axis, defined by a half-height and radius.
 * The shape starts at its largest radius at the bottom, and approaches zero at the top.
 *
 * **Note:** the axis that the cone rests on cannot be defined here. Instead, transform the collider that the
 * shape is placed on.
 * @param halfHeight The half-height of the cone. Must be greater than 0.
 * @param radius The radius of the cone. Must be greater than 0.
 */
@ConfigSerializable
data class Cone(
    val halfHeight: Real,
    val radius: Real,
) : ConvexGeometry {
    init {
        require(halfHeight > 0.0) { "requires halfHeight > 0.0" }
        require(radius > 0.0) { "requires radius > 0.0" }
    }
}
