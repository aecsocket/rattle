package io.gitlab.aecsocket.ignacio.bullet

import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.collision.shapes.SphereCollisionShape
import com.jme3.bullet.objects.PhysicsGhostObject
import com.jme3.bullet.objects.PhysicsRigidBody
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

    val mBodies = HashMap<PhysicsRigidBody, BltRigidBody>()
    override val bodies get() = mBodies.values
    val mBodiesAwake = ArrayList<BltRigidBody>()
    override val bodiesAwake get() = mBodiesAwake

    private inline fun assertThread() = backend.assertThread()

    override fun addBody(body: IgBody) {
        assertThread()
        body as BltRigidBody
        mBodies[body.handle] = body
        handle.addCollisionObject(body.handle)
    }

    override fun removeBody(body: IgBody) {
        assertThread()
        body as BltRigidBody
        mBodies.remove(body.handle)
        handle.removeCollisionObject(body.handle)
    }

    override fun countBodies(onlyAwake: Boolean): Int {
        assertThread()
        return if (onlyAwake) mBodiesAwake.size
        else mBodies.size
    }

    override fun nearbyBodies(position: Vec3, radius: IgScalar): List<IgBody> {
        assertThread()
        val ghost = PhysicsGhostObject(SphereCollisionShape(radius.toFloat()))
        ghost.position = position
        handle.addCollisionObject(ghost)
        val overlaps = ghost.overlappingObjects
        handle.removeCollisionObject(ghost)
        return overlaps.mapNotNull { mBodies[it] }
    }

    fun step() {
        handle.update(settings.stepInterval.toFloat(), backend.settings.maxSubSteps)
        mBodiesAwake.clear()
        handle.rigidBodyList.forEach { body ->
            if (body.isActive)
                mBodiesAwake.add(mBodies[body]!!)
        }
    }
}
