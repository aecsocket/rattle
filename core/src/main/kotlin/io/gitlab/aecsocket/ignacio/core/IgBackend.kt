package io.gitlab.aecsocket.ignacio.core

import io.gitlab.aecsocket.ignacio.core.math.Transform

typealias IgScalar = Double

interface IgBackend<S> {
    fun reload(settings: S)

    fun createSpace(settings: IgPhysicsSpace.Settings): IgPhysicsSpace

    fun destroySpace(space: IgPhysicsSpace)

    fun createStaticBody(shape: IgShape, transform: Transform): IgStaticBody

    fun createDynamicBody(shape: IgShape, transform: Transform, dynamics: IgBodyDynamics): IgDynamicBody

    fun step(spaces: Iterable<IgPhysicsSpace>)

    fun destroy()
}
