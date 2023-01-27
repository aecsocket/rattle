package io.gitlab.aecsocket.ignacio.bullet

import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.objects.PhysicsRigidBody
import io.gitlab.aecsocket.ignacio.core.IgBody
import io.gitlab.aecsocket.ignacio.core.IgPhysicsSpace
import io.gitlab.aecsocket.ignacio.core.math.Vec3

class BltPhysicsSpace(
    private val backend: BulletBackend,
    val handle: PhysicsSpace,
    settings: IgPhysicsSpace.Settings,
    val ground: PhysicsRigidBody
) : IgPhysicsSpace {
    override var settings = settings
        set(value) {
            field = value
            ground.position = Vec3(0.0, settings.groundPlaneY, 0.0)
        }

    private inline fun assertThread() = backend.assertThread()

    override fun addBody(body: IgBody) {
        assertThread()
        body as BltRigidBody
        handle.addCollisionObject(body.handle)
    }

    override fun removeBody(body: IgBody) {
        assertThread()
        body as BltRigidBody
        handle.removeCollisionObject(body.handle)
    }

    override fun countBodies(onlyAwake: Boolean): Int {
        assertThread()
        return if (onlyAwake) handle.rigidBodyList
            .filter { !it.isStatic && it.isActive }
            .size
        else handle.countRigidBodies()
    }
}
