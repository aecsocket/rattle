package io.gitlab.aecsocket.ignacio.bullet

import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsGhostObject
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.bullet.objects.PhysicsSoftBody
import com.jme3.math.Quaternion
import com.jme3.math.TransformDp
import com.jme3.math.Vector3f
import com.simsilica.mathd.Quatd
import com.simsilica.mathd.Vec3d
import io.gitlab.aecsocket.ignacio.core.Quat
import io.gitlab.aecsocket.ignacio.core.Transform
import io.gitlab.aecsocket.ignacio.core.Vec3

fun Vec3.btDp() = Vec3d(x, y, z)
fun Vec3.btSp() = Vector3f(x.toFloat(), y.toFloat(), z.toFloat())
fun Vec3d.ig() = Vec3(x, y, z)
fun Vector3f.ig() = Vec3(x.toDouble(), y.toDouble(), z.toDouble())

fun Quat.btDp() = Quatd(x, y, z, w)
fun Quat.btSp() = Quaternion(x.toFloat(), y.toFloat(), z.toFloat(), w.toFloat())
fun Quatd.ig() = Quat(x, y, z, w)
fun Quaternion.ig() = Quat(x.toDouble(), y.toDouble(), z.toDouble(), w.toDouble())

fun Transform.btDp() = TransformDp(position.btDp(), rotation.btDp())
fun TransformDp.ig() = Transform(translation.ig(), rotation.ig())

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
