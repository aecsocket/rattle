package io.github.aecsocket.ignacio.core

interface IgnacioEngine : Destroyable {
    val build: String

    fun runTask(task: Runnable)

    fun createGeometry(settings: GeometrySettings): Geometry

    fun createSpace(settings: PhysicsSpace.Settings): PhysicsSpace
}
