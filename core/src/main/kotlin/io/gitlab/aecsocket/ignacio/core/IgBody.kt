package io.gitlab.aecsocket.ignacio.core

import io.gitlab.aecsocket.ignacio.core.math.Transform
import io.gitlab.aecsocket.ignacio.core.math.Vec3
import org.spongepowered.configurate.objectmapping.ConfigSerializable

interface IgShape {
    val geometry: IgGeometry
    val transform: Transform

    fun destroy()
}

@ConfigSerializable
data class IgBodyDynamics(
    val mass: IgScalar = 1.0
)

interface IgBody {
    var transform: Transform
    val shapes: Collection<IgShape>

    fun isAdded(): Boolean

    fun setGeometry(geometry: IgGeometry)

    fun attachShape(shape: IgShape)

    fun detachShape(shape: IgShape)

    fun detachAllShapes()

    fun destroy()
}

interface IgRigidBody : IgBody {

}

interface IgStaticBody : IgRigidBody {

}

interface IgDynamicBody : IgRigidBody {
    var linearVelocity: Vec3
    var angularVelocity: Vec3

    val sleeping: Boolean

    fun wake()
}
