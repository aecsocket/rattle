package io.github.aecsocket.ignacio

import io.github.aecsocket.klam.*
import kotlinx.coroutines.CoroutineScope

typealias Quat = FQuat
typealias Transform = DAffine3
typealias RRay = DRay3

interface BodyLayer

enum class BodyLayerType {
    STATIC,
    MOVING,
    TERRAIN,
}

interface BodyFlag

interface BodyContactFilter

interface LayerFilter : Destroyable

interface BodyFilter : Destroyable

interface ShapeFilter : Destroyable

interface IgnacioEngine : Destroyable {
    val build: String

    val layers: Layers
    interface Layers {
        val static: BodyLayer

        val moving: BodyLayer

        val terrain: BodyLayer

        val entity: BodyLayer
    }

    val filters: Filters
    interface Filters {
        val anyLayer: LayerFilter

        val anyBody: BodyFilter

        val anyShape: ShapeFilter

        // TODO
//        fun forLayer(layer: Filter<BodyLayer>, flag: Filter<BodyFlag>): LayerFilter
//
//        fun forBody(test: Predicate<PhysicsBody.Read>): BodyFilter

        // TODO fun forShape(test: )
    }

    fun runTask(block: Runnable)

    fun launchTask(block: suspend CoroutineScope.() -> Unit)

    fun contactFilter(layer: BodyLayer, flags: Set<BodyFlag>): BodyContactFilter

    fun contactFilter(layer: BodyLayer, vararg flags: BodyFlag) = contactFilter(layer, setOf(*flags))

    fun shape(geom: Geometry): Shape

    fun space(settings: PhysicsSpace.Settings): PhysicsSpace

    interface Builder {
        // TODO
//        fun defineBodyLayer(type: BodyLayerType): BodyLayer
//
//        fun defineBodyFlag(): BodyFlag

        fun build(): IgnacioEngine
    }
}
