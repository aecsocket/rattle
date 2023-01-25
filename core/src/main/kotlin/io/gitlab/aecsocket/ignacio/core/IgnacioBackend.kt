package io.gitlab.aecsocket.ignacio.core

typealias IgScalar = Double

interface IgnacioBackend {
    fun createSpace(settings: IgSpaceSettings): IgPhysicsSpace

    fun createStaticBody(shape: IgShape, transform: Transform): IgStaticBody

    fun createDynamicBody(shape: IgShape, transform: Transform, dynamics: IgBodyDynamics): IgDynamicBody
}
