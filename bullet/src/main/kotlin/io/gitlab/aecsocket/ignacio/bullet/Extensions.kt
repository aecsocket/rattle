package io.gitlab.aecsocket.ignacio.bullet

import com.jme3.bounding.BoundingBox
import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsGhostObject
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.bullet.objects.PhysicsSoftBody
import com.jme3.math.Matrix3f
import com.jme3.math.Quaternion
import com.jme3.math.TransformDp
import com.jme3.math.Vector3f
import com.simsilica.mathd.Quatd
import com.simsilica.mathd.Vec3d
import io.gitlab.aecsocket.ignacio.core.math.AABB
import io.gitlab.aecsocket.ignacio.core.math.Quat
import io.gitlab.aecsocket.ignacio.core.math.Transform
import io.gitlab.aecsocket.ignacio.core.math.Vec3

fun Vec3.btDp() = Vec3d(x, y, z)
fun Vec3.btSp() = Vector3f(x.toFloat(), y.toFloat(), z.toFloat())
fun Vec3d.ig() = Vec3(x, y, z)
fun Vector3f.ig() = Vec3(x.toDouble(), y.toDouble(), z.toDouble())

fun Quat.btDp() = Quatd(x, y, z, w)
fun Quat.btSp() = Quaternion(x.toFloat(), y.toFloat(), z.toFloat(), w.toFloat())
fun Quatd.ig() = Quat(x, y, z, w)
fun Quaternion.ig() = Quat(x.toDouble(), y.toDouble(), z.toDouble(), w.toDouble())

fun Transform.btDp() = TransformDp(position.btDp(), rotation.btDp())
fun Transform.btSp() = com.jme3.math.Transform(position.btSp(), rotation.btSp())
fun TransformDp.ig() = Transform(translation.ig(), rotation.ig())

fun AABB.btSp() = BoundingBox(min.btSp(), max.btSp())
fun BoundingBox.ig() = AABB(min, max)

val BoundingBox.min: Vec3
    get() = getMin(Vector3f()).ig()
val BoundingBox.max: Vec3
    get() = getMax(Vector3f()).ig()

var PhysicsSpace.gravity: Vec3
    get() = getGravity(Vector3f()).ig()
    set(value) { setGravity(value.btSp()) }

var PhysicsCollisionObject.position: Vec3
    get() = getPhysicsLocationDp(Vec3d()).ig()
    set(value) {
        when (this) {
            is PhysicsGhostObject -> position = value
            is PhysicsRigidBody -> position = value
            is PhysicsSoftBody -> position = value
        }
    }
var PhysicsGhostObject.position: Vec3
    get() = getPhysicsLocationDp(Vec3d()).ig()
    set(value) { setPhysicsLocationDp(value.btDp()) }
var PhysicsRigidBody.position: Vec3
    get() = getPhysicsLocationDp(Vec3d()).ig()
    set(value) { setPhysicsLocationDp(value.btDp()) }
var PhysicsSoftBody.position: Vec3
    get() = getPhysicsLocationDp(Vec3d()).ig()
    set(value) { setPhysicsLocationDp(value.btDp()) }

var PhysicsCollisionObject.rotation: Quat
    get() = getPhysicsRotationDp(Quatd()).ig()
    set(value) {
        when (this) {
            is PhysicsGhostObject -> rotation = value
            is PhysicsRigidBody -> rotation = value
            is PhysicsSoftBody -> throw UnsupportedOperationException("Cannot set rotation of soft body")
        }
    }
var PhysicsGhostObject.rotation: Quat
    get() = getPhysicsRotationDp(Quatd()).ig()
    set(value) { setPhysicsRotationDp(value.btDp()) }
var PhysicsRigidBody.rotation: Quat
    get() = getPhysicsRotationDp(Quatd()).ig()
    set(value) { setPhysicsRotationDp(value.btDp()) }
val PhysicsSoftBody.rotation: Quat
    get() = getPhysicsRotationDp(Quatd()).ig()

var PhysicsCollisionObject.transform: Transform
    get() = Transform(position, rotation)
    set(value) {
        position = value.position
        rotation = value.rotation
    }

var PhysicsRigidBody.linearVelocity: Vec3
    get() = getLinearVelocityDp(Vec3d()).ig()
    set(value) { setLinearVelocityDp(value.btDp()) }
var PhysicsRigidBody.angularVelocity: Vec3
    get() = getAngularVelocityDp(Vec3d()).ig()
    set(value) { setAngularVelocityDp(value.btDp()) }

fun PhysicsCollisionObject.boundingBox(): AABB {
    val box = collisionShape.boundingBox(Vector3f.ZERO, Matrix3f.IDENTITY, BoundingBox())
    val position = position
    return AABB(box.min + position, box.max + position)
}

private typealias JRandom = java.util.Random
private typealias KRandom = kotlin.random.Random

fun JRandom.nextVec3() = Vec3(nextDouble(), nextDouble(), nextDouble())
fun KRandom.nextVec3() = Vec3(nextDouble(), nextDouble(), nextDouble())
