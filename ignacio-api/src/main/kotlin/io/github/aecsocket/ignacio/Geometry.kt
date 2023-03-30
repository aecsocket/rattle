package io.github.aecsocket.ignacio

import org.spongepowered.configurate.objectmapping.ConfigSerializable

sealed interface Geometry

const val DEFAULT_CONVEX_RADIUS = 0.05f

sealed interface ConvexGeometry : Geometry

@ConfigSerializable
data class SphereGeometry(
    val radius: Float,
) : ConvexGeometry

@ConfigSerializable
data class BoxGeometry(
    val halfExtent: Vec3,
    val convexRadius: Float = DEFAULT_CONVEX_RADIUS,
) : ConvexGeometry

@ConfigSerializable
data class CapsuleGeometry(
    val halfHeight: Float,
    val radius: Float,
) : ConvexGeometry

@ConfigSerializable
data class TaperedCapsuleGeometry(
    val halfHeight: Float,
    val topRadius: Float,
    val bottomRadius: Float,
) : ConvexGeometry

@ConfigSerializable
data class CylinderGeometry(
    val halfHeight: Float,
    val radius: Float,
    val convexRadius: Float,
) : ConvexGeometry

data class CompoundChild(
    val shape: Shape,
    val position: Vec3,
    val rotation: Quat,
)

sealed interface CompoundGeometry : Geometry {
    val children: List<CompoundChild>
}

data class StaticCompoundGeometry(
    override val children: List<CompoundChild>,
) : CompoundGeometry

data class MutableCompoundGeometry(
    override val children: List<CompoundChild>,
) : CompoundGeometry

interface Shape : Destroyable
