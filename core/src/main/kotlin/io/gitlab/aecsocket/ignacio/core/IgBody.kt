package io.gitlab.aecsocket.ignacio.core

import io.gitlab.aecsocket.ignacio.core.math.AABB
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
    val boundingBox: AABB

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
    var kinematic: Boolean
    val active: Boolean

    fun activate()

    fun applyForce(force: Vec3)

    fun applyForceImpulse(force: Vec3)

    fun applyTorque(torque: Vec3)

    fun applyTorqueImpulse(torque: Vec3)
}
