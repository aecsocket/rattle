package io.github.aecsocket.rattle

import org.spongepowered.configurate.objectmapping.ConfigSerializable

sealed interface Geometry

sealed interface ConvexGeometry : Geometry

@ConfigSerializable
data class Sphere(
    val radius: Real,
) : ConvexGeometry {
    init {
        require(radius > 0.0) { "requires radius > 0.0" }
    }
}

@ConfigSerializable
data class Box(
    val halfExtent: Vec,
) : ConvexGeometry {
    init {
        require(halfExtent.x > 0.0) { "requires halfExtent.x > 0.0 " }
        require(halfExtent.y > 0.0) { "requires halfExtent.y > 0.0 " }
        require(halfExtent.z > 0.0) { "requires halfExtent.z > 0.0 " }
    }
}

@ConfigSerializable
data class Capsule(
    val halfHeight: Real,
    val radius: Real,
) : ConvexGeometry {
    init {
        require(halfHeight > 0.0) { "requires halfHeight > 0.0" }
        require(radius > 0.0) { "requires radius > 0.0" }
    }
}
