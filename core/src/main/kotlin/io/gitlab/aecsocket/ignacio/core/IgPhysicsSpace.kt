package io.gitlab.aecsocket.ignacio.core

import org.spongepowered.configurate.objectmapping.ConfigSerializable

@ConfigSerializable
data class IgSpaceSettings(
    val gravity: Vec3 = Vec3(0.0, -9.81, 0.0)
)

interface IgPhysicsSpace {
    fun addBody(body: IgBody)

    fun destroy()
}
