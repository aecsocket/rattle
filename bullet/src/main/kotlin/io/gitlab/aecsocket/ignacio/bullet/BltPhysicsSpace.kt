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

    override fun step() {
        handle.update(settings.stepInterval.toFloat(), backend.settings.maxSubSteps)
    }

    override fun addBody(body: IgBody) {
        body as BltRigidBody
        handle.addCollisionObject(body.handle)
    }

    override fun removeBody(body: IgBody) {
        body as BltRigidBody
        handle.removeCollisionObject(body.handle)
    }
}
