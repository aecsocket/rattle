package io.github.aecsocket.ignacio.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable

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

    fun runTask(block: Runnable)

    fun launchTask(block: suspend CoroutineScope.() -> Unit)

    fun createShape(geometry: Geometry): Shape

    fun createSpace(settings: PhysicsSpace.Settings): PhysicsSpace
}
