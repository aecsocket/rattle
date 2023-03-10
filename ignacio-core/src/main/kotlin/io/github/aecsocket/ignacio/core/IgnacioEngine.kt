package io.github.aecsocket.ignacio.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable

interface BroadPhaseLayer

interface ObjectLayer

interface BroadFilter : Destroyable

fun interface BroadFilterTest {
    fun test(layer: BroadPhaseLayer): Boolean
}

fun interface ObjectFilterTest {
    fun test(layer: ObjectLayer): Boolean
}

interface IgnacioEngine : Destroyable {
    val build: String

    val layers: Layers
    interface Layers {
        val ofBroadPhase: OfBroadPhase
        interface OfBroadPhase {
            val static: BroadPhaseLayer

            val terrain: BroadPhaseLayer

            val entity: BroadPhaseLayer

            val moving: BroadPhaseLayer
        }

        val ofObject: OfObject
        interface OfObject {
            val static: ObjectLayer

            val terrain: ObjectLayer

            val entity: ObjectLayer

            val moving: ObjectLayer
        }
    }

    val filters: Filters
    interface Filters {
        fun createBroad(
            broad: BroadFilterTest,
            objects: ObjectFilterTest,
        ): BroadFilter
    }

    fun runTask(block: Runnable)

    fun launchTask(block: suspend CoroutineScope.() -> Unit)

    fun createShape(geometry: Geometry): Shape

    fun createSpace(settings: PhysicsSpace.Settings): PhysicsSpace
}
