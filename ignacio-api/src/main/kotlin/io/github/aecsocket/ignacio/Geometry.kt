package io.github.aecsocket.ignacio

import io.github.aecsocket.klam.FVec3
import org.spongepowered.configurate.objectmapping.ConfigSerializable

const val DEFAULT_CONVEX_RADIUS = 0.05f
const val DEFAULT_DENSITY = 1000.0f

sealed interface Geometry

sealed interface ConvexGeometry : Geometry {
    val density: Float
}

@ConfigSerializable
data class SphereGeometry(
    val radius: Float,
    override val density: Float = DEFAULT_DENSITY,
) : ConvexGeometry

@ConfigSerializable
data class BoxGeometry(
    val halfExtent: FVec3,
    val convexRadius: Float = DEFAULT_CONVEX_RADIUS,
    override val density: Float = DEFAULT_DENSITY,
) : ConvexGeometry

@ConfigSerializable
data class CapsuleGeometry(
    val halfHeight: Float,
    val radius: Float,
    override val density: Float = DEFAULT_DENSITY,
) : ConvexGeometry

@ConfigSerializable
data class TaperedCapsuleGeometry(
    val halfHeight: Float,
    val topRadius: Float,
    val bottomRadius: Float,
    override val density: Float = DEFAULT_DENSITY,
) : ConvexGeometry

@ConfigSerializable
data class CylinderGeometry(
    val halfHeight: Float,
    val radius: Float,
    val convexRadius: Float,
    override val density: Float = DEFAULT_DENSITY,
) : ConvexGeometry

data class CompoundChild(
    val shape: Shape,
    val position: FVec3,
    val rotation: Quat,
)

sealed interface CompoundGeometry : Geometry {
    val children: Collection<CompoundChild>
}

data class StaticCompoundGeometry(
    override val children: Collection<CompoundChild>,
) : CompoundGeometry

data class MutableCompoundGeometry(
    override val children: Collection<CompoundChild>,
) : CompoundGeometry

interface Shape : Destroyable
