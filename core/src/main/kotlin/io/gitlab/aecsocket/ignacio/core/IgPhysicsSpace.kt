package io.gitlab.aecsocket.ignacio.core

import org.spongepowered.configurate.objectmapping.ConfigSerializable

interface IgPhysicsSpace {
    @ConfigSerializable
    data class Settings(
        val gravity: Vec3 = Vec3(0.0, -9.81, 0.0),
        val stepInterval: IgScalar = 0.05,
        val groundPlaneY: IgScalar = -128.0
    )

    var settings: Settings

    fun step()

    fun addBody(body: IgBody)

    fun removeBody(body: IgBody)
}
