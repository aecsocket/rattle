package io.gitlab.aecsocket.ignacio.core

sealed interface IgShape

data class IgSphereShape(val radius: IgScalar) : IgShape

data class IgBoxShape(val halfExtent: Vec3) : IgShape

object IgPlaneShape : IgShape
