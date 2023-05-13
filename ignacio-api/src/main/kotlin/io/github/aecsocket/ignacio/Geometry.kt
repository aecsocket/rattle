package io.github.aecsocket.ignacio

import org.spongepowered.configurate.objectmapping.ConfigSerializable

sealed interface Geometry

sealed interface ConvexGeometry : Geometry

@ConfigSerializable
data class Sphere(
    val radius: Real,
) : ConvexGeometry

@ConfigSerializable
data class Cuboid(
    val halfExtent: Vec,
) : ConvexGeometry

@ConfigSerializable
data class Capsule(
    val halfHeight: Real,
    val radius: Real,
) : ConvexGeometry
