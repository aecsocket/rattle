package io.gitlab.aecsocket.ignacio.core

import io.gitlab.aecsocket.ignacio.core.math.Vec3

sealed interface IgShape

data class IgSphereShape(val radius: IgScalar) : IgShape

data class IgBoxShape(val halfExtent: Vec3) : IgShape

object IgPlaneShape : IgShape
