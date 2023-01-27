package io.gitlab.aecsocket.ignacio.core

import io.gitlab.aecsocket.ignacio.core.math.Vec3
import org.spongepowered.configurate.objectmapping.ConfigSerializable

interface IgPhysicsSpace {
    @ConfigSerializable
    data class Settings(
        val gravity: Vec3 = Vec3(0.0, -9.81, 0.0),
        val stepInterval: IgScalar = 0.05,
        val groundPlaneY: IgScalar = -128.0
    )

    var settings: Settings

    fun addBody(body: IgBody)

    fun removeBody(body: IgBody)

    fun countBodies(onlyAwake: Boolean = false): Int

    fun nearbyBodies(position: Vec3, radius: IgScalar): List<IgBody>
}
