package io.github.aecsocket.ignacio

import io.github.aecsocket.klam.DAffine3
import io.github.aecsocket.klam.DVec3
import io.github.aecsocket.klam.FQuat
import io.github.aecsocket.klam.FVec3

typealias Vec3 = FVec3
typealias RVec3 = DVec3
typealias Quat = FQuat
typealias Transform = DAffine3

interface ObjectLayer

interface ObjectLayerKey

interface IgnacioEngine : Destroyable {
    val build: String

    val objectLayers: ObjectLayers
    interface ObjectLayers {
        val static: ObjectLayer

        val moving: ObjectLayer

        val terrain: ObjectLayer

        val entity: ObjectLayer
    }

    fun createShape(geom: Geometry): Shape

    fun createSpace(settings: PhysicsSpace.Settings): PhysicsSpace
}
