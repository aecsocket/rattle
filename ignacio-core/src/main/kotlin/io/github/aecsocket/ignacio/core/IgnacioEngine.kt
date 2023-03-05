package io.github.aecsocket.ignacio.core

import kotlinx.coroutines.CoroutineScope

interface IgnacioEngine : Destroyable {
    val build: String

    val layers: Layers
    interface Layers {
        val ofObject: OfObject
        interface OfObject {
            val static: ObjectLayer

            val terrain: ObjectLayer

            val entity: ObjectLayer

            val moving: ObjectLayer
        }
    }

    fun runTask(block: suspend CoroutineScope.() -> Unit)

    fun createGeometry(settings: GeometrySettings): Geometry

    fun createSpace(settings: PhysicsSpace.Settings): PhysicsSpace
}
