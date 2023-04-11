package io.github.aecsocket.ignacio

import io.github.aecsocket.alexandria.assertGt
import io.github.aecsocket.alexandria.assertGtEq
import io.github.aecsocket.klam.FQuat
import io.github.aecsocket.klam.FVec3
import io.github.aecsocket.klam.minComponent
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
) : ConvexGeometry {
    init {
        assertGt("radius", 0.0f, radius)
        assertGt("density", 0.0f, density)
    }
}

@ConfigSerializable
data class BoxGeometry(
    val halfExtent: FVec3,
    val convexRadius: Float = DEFAULT_CONVEX_RADIUS,
    override val density: Float = DEFAULT_DENSITY,
) : ConvexGeometry {
    init {
        assertGt("minComponent(halfExtent)", 0.0f, minComponent(halfExtent))
        assertGtEq("convexRadius", 0.0f, convexRadius)
        assertGt("density", 0.0f, density)
    }
}

@ConfigSerializable
data class CapsuleGeometry(
    val halfHeight: Float,
    val radius: Float,
    override val density: Float = DEFAULT_DENSITY,
) : ConvexGeometry {
    init {
        assertGt("halfHeight", 0.0f, halfHeight)
        assertGt("radius", 0.0f, radius)
        assertGt("density", 0.0f, density)
    }
}

@ConfigSerializable
data class TaperedCapsuleGeometry(
    val halfHeight: Float,
    val topRadius: Float,
    val bottomRadius: Float,
    override val density: Float = DEFAULT_DENSITY,
) : ConvexGeometry {
    init {
        assertGt("halfHeight", 0.0f, halfHeight)
        assertGt("topRadius", 0.0f, topRadius)
        assertGt("bottomRadius", 0.0f, bottomRadius)
        assertGt("density", 0.0f, density)
    }
}

@ConfigSerializable
data class CylinderGeometry(
    val halfHeight: Float,
    val radius: Float,
    val convexRadius: Float,
    override val density: Float = DEFAULT_DENSITY,
) : ConvexGeometry {
    init {
        assertGt("halfHeight", 0.0f, halfHeight)
        assertGt("radius", 0.0f, radius)
        assertGtEq("convexRadius", 0.0f, convexRadius)
        assertGt("density", 0.0f, density)
    }
}

data class CompoundChild(
    val shape: Shape,
    val position: FVec3,
    val rotation: FQuat,
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
