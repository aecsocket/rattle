package io.gitlab.aecsocket.ignacio.core

import io.gitlab.aecsocket.ignacio.core.math.Quat
import io.gitlab.aecsocket.ignacio.core.math.Transform
import kotlin.math.sqrt

typealias IgScalar = Double

val groundPlaneQuat = Quat(0.0, 0.0, 1 / sqrt(2.0), 1 / sqrt(2.0))

interface IgBackend<S> {
    fun reload(settings: S)

    fun createShape(geometry: IgGeometry, transform: Transform = Transform.Identity): IgShape

    fun createStaticBody(transform: Transform): IgStaticBody

    fun createDynamicBody(transform: Transform, dynamics: IgBodyDynamics): IgDynamicBody

    fun createSpace(settings: IgPhysicsSpace.Settings): IgPhysicsSpace

    fun destroySpace(space: IgPhysicsSpace)

    fun step(spaces: Iterable<IgPhysicsSpace>)

    fun destroy()
}
