package io.github.aecsocket.ignacio.core

import io.github.aecsocket.ignacio.core.math.Vec3d
import io.github.aecsocket.ignacio.core.math.Vec3f

data class ContactManifold(
    val position: Vec3d,
    val penetrationDepth: Float,
    val normal: Vec3f,
)
