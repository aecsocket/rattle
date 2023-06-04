package io.github.aecsocket.rattle

import io.github.aecsocket.klam.IVec3
import org.spongepowered.configurate.objectmapping.ConfigSerializable

const val DEFAULT_MARGIN: Real = 0.05

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
 *
 * Geometries may have a "margin", "convex radius" or "border radius", which is an extra scalar value which adds a bit
 * of extra volume to the outside of the shape, making the shape rounded. This is used to improve performance during
 * collision detection, since this margin means less work has to be done during narrow-phase collision resolution
 * (finding contact normals and penetration depth).
 * - If you're unsure, you should leave this as the default.
 * - If you want to disable this, set it to `0.0`.
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
 *
 * **Note:** adding a convex margin actually *decreases* performance on cuboid shapes.
 * @param halfExtent The half-lengths of the box. Components must all be greater than 0.
 */
@ConfigSerializable
data class Box(
    val halfExtent: Vec,
    val margin: Real = 0.0,
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
    val margin: Real = DEFAULT_MARGIN,
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
    val margin: Real = DEFAULT_MARGIN,
) : ConvexGeometry {
    init {
        require(halfHeight > 0.0) { "requires halfHeight > 0.0" }
        require(radius > 0.0) { "requires radius > 0.0" }
    }
}

/**
 * A convex shape defined by a set of points, which will be used to compute a convex hull (smallest convex shape
 * containing the given points) when baked into a [Shape].
 *
 * If you are taking input from a file or other unverified source, this is the best option to use to ensure the
 * inputs are actually convex.
 * @param points The points on the hull.
 * @param margin The convex margin of the shape. See [ConvexGeometry].
 */
data class ConvexHull(
    val points: List<Vec>,
    val margin: Real = DEFAULT_MARGIN,
) : ConvexGeometry

/**
 * A shape **assumed** to be already convex, defined by an array of vertices and array of triangle face indices.
 * If the shape is not convex, collision detection will not be accurate. Build a [Shape] out of this is faster than
 * making one out of a [ConvexHull], but is not as safe.
 *
 * If you are taking input from a file or other unverified source, consider using a [ConvexHull] instead.
 * @param vertices The vertex buffer of the mesh.
 * @param indices The indices buffer, determining which faces are made up of which vertices.
 * @param margin The convex margin of the shape. See [ConvexGeometry].
 */
data class ConvexMesh(
    val vertices: List<Vec>,
    val indices: List<IVec3>,
    val margin: Real = DEFAULT_MARGIN,
) : ConvexGeometry

/**
 * A shape of **unknown** convexity which, when turned into a [Shape], will be decomposed into a [Compound] of
 * convex shapes using the implementation's convex decomposition algorithm (VHACD). Note that decomposition is
 * **very slow** (relatively), so this step should be precomputed or cached in some form.
 * @param vertices The vertex buffer of the mesh.
 * @param indices The indices buffer, determining which faces are made up of which vertices.
 * @param vhacd The settings for the VHACD algorithm.
 * @param margin The convex margin of the shape. See [ConvexGeometry].
 */
data class ConvexDecomposition(
    val vertices: List<Vec>,
    val indices: List<IVec3>,
    val vhacd: VhacdSettings = VhacdSettings(),
    val margin: Real = DEFAULT_MARGIN,
) : ConvexGeometry

/**
 * Options for the VHACD convex decomposition process. See [VHACD](https://github.com/Unity-Technologies/VHACD#parameters)
 * for details.
 * @param concavity Maximum concavity [0.0, 1.0]
 * @param alpha Bias towards clipping along symmetry planes [0.0, 1.0]
 * @param beta Bias towards clipping along revolution planes [0.0, 1.0]
 * @param resolution Resolution used during voxelization (minimum 0)
 * @param planeDownsampling Granularity of the search for the best clipping plane (minimum 0)
 * @param convexHullDownsampling Precision of convex-hull generation during the clipping plane selection stage (minimum 0)
 * @param fillMode The way the input mesh gets voxelized
 * @param convexHullApproximation Whether the convex-hull should be approximated during decomposition.
 *                                Setting to `true` increases performance with a slight degradation of decomposition quality.
 * @param maxConvexHulls Max number of convex hulls generated by decomposition (minimum 0)
 */
@ConfigSerializable
data class VhacdSettings(
    val concavity: Real = 0.01,
    val alpha: Real = 0.05,
    val beta: Real = 0.05,
    val resolution: Int = 64,
    val planeDownsampling: Int = 4,
    val convexHullDownsampling: Int = 4,
    val fillMode: FillMode = FillMode.FloodFill(),
    val convexHullApproximation: Boolean = true,
    val maxConvexHulls: Int = 1024,
) {
    init {
        require(concavity >= 0.0) { "requires concavity >= 0.0" }
        require(concavity <= 1.0) { "requires concavity <= 1.0" }

        require(alpha >= 0.0) { "requires alpha >= 0.0" }
        require(alpha <= 1.0) { "requires alpha <= 1.0" }

        require(beta >= 0.0) { "requires beta >= 0.0" }
        require(beta <= 1.0) { "requires beta <= 1.0" }

        require(resolution > 0) { "requires resolution > 0" }
        require(planeDownsampling > 0) { "requires planeDownsampling > 0" }
        require(convexHullDownsampling > 0) { "requires convexHullDownsampling > 0" }
        require(maxConvexHulls > 0) { "requires maxConvexHulls > 0" }
    }

    /**
     * Controls how the voxelization determines which voxel needs to be considered empty, and which ones will be
     * considered full.
     */
    sealed interface FillMode {
        /**
         * Only consider full the voxels intersecting the surface of the shape being voxelized.
         */
        /* TODO: Kotlin 1.9 data */ object SurfaceOnly : FillMode

        /**
         * Use a flood-fill technique to consider full the voxels intersecting the surface of the shape being
         * voxelized, as well as all the voxels bounded of them.
         * @param detectCavities Detects holes inside of a solid contour.
         */
        data class FloodFill(
            val detectCavities: Boolean = false,
        ) : FillMode
    }
}

/**
 * A shape consisting of multiple already-created sub-[Shape]s, which can be positioned at different [Child.delta]
 * offsets to the root compound. Shapes from a [Compound] can *not* be a child.
 * @param children The child shapes of this compound. Must not contain [Compound] shapes.
 */
data class Compound(
    val children: List<Child>,
) : Geometry {
    init {
        require(children.isNotEmpty()) { "requires children.isNotEmpty()" }
    }

    /**
     * A child in a [Compound] geometry.
     * @param shape The shape.
     * @param delta The position offset from the root compound.
     */
    data class Child(
        val shape: Shape,
        val delta: Iso = Iso(),
    )
}
