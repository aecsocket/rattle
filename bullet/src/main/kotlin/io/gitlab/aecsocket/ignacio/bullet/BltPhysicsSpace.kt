package io.gitlab.aecsocket.ignacio.bullet

import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.collision.shapes.SphereCollisionShape
import com.jme3.bullet.objects.PhysicsGhostObject
import io.gitlab.aecsocket.ignacio.core.IgBody
import io.gitlab.aecsocket.ignacio.core.IgPhysicsSpace
import io.gitlab.aecsocket.ignacio.core.IgScalar
import io.gitlab.aecsocket.ignacio.core.groundPlaneQuat
import io.gitlab.aecsocket.ignacio.core.math.Transform
import io.gitlab.aecsocket.ignacio.core.math.Vec3

class BltPhysicsSpace(
    private val backend: BulletBackend,
    val handle: PhysicsSpace,
    settings: IgPhysicsSpace.Settings,
    val ground: BltRigidBody
) : IgPhysicsSpace {
    override var settings = settings
        set(value) {
            field = value
            ground.transform = Transform(Vec3(0.0, settings.groundPlaneY, 0.0), groundPlaneQuat)
        }

    val bodies = HashMap<Long, BltRigidBody>()

    private inline fun assertThread() = backend.assertThread()

    override fun addBody(body: IgBody) {
        assertThread()
        body as BltRigidBody
        bodies[body.handle.nativeId()] = body
        handle.addCollisionObject(body.handle)
    }

    override fun removeBody(body: IgBody) {
        assertThread()
        body as BltRigidBody
        bodies.remove(body.handle.nativeId())
        handle.removeCollisionObject(body.handle)
    }

    override fun countBodies(onlyAwake: Boolean): Int {
        assertThread()
        return if (onlyAwake) handle.rigidBodyList
            .filter { !it.isStatic && it.isActive }
            .size
        else handle.countRigidBodies()
    }

    override fun nearbyBodies(position: Vec3, radius: IgScalar): List<IgBody> {
        assertThread()
        val ghost = PhysicsGhostObject(SphereCollisionShape(radius.toFloat()))
        ghost.position = position
        handle.addCollisionObject(ghost)
        val overlaps = ghost.overlappingObjects
        handle.removeCollisionObject(ghost)
        return overlaps.mapNotNull { bodies[it.nativeId()] }
    }
}
