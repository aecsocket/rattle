package io.gitlab.aecsocket.ignacio.core

import io.gitlab.aecsocket.ignacio.core.math.Vec3

sealed interface IgGeometry

object IgPlaneGeometry : IgGeometry

data class IgSphereGeometry(val radius: IgScalar) : IgGeometry

data class IgBoxGeometry(val halfExtent: Vec3) : IgGeometry
