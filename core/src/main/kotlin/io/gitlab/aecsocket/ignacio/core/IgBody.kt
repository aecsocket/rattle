package io.gitlab.aecsocket.ignacio.core

import org.spongepowered.configurate.objectmapping.ConfigSerializable

@ConfigSerializable
data class IgBodyDynamics(
    val mass: IgScalar = 1.0
)

interface IgBody {
    var transform: Transform

    fun destroy()
}

interface IgRigidBody : IgBody {

}

interface IgStaticBody : IgRigidBody {

}

interface IgDynamicBody : IgRigidBody {

}
