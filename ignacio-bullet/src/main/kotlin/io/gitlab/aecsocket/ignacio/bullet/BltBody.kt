package io.gitlab.aecsocket.ignacio.bullet

import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import io.gitlab.aecsocket.ignacio.core.*
import io.gitlab.aecsocket.ignacio.core.math.AABB
import io.gitlab.aecsocket.ignacio.core.math.Transform
import io.gitlab.aecsocket.ignacio.core.math.Vec3

data class BltShape(
    val handle: CollisionShape,
    override val geometry: IgGeometry,
    override val transform: Transform
) : IgShape {
    override fun destroy() {}
}

private data class IgShapeImpl(
    override val geometry: IgGeometry
) : IgShape {
    override val transform get() = Transform.Identity

    override fun destroy() {}
}

sealed class BltBody(
    protected val backend: BulletBackend,
    open val handle: PhysicsCollisionObject
) : IgBody {
    override var transform: Transform
        get() {
            assertThread()
            return handle.transform
        }
        set(value) {
            assertThread()
            handle.transform = value
        }

    // the body will automatically alternate between three modes:
    // - empty shape
    // - single shape
    // - compound shape
    var mGeometry: IgGeometry? = null

    val mShapes = HashMap<Long, IgShape>()
    var mCompound: CompoundCollisionShape? = null
    override val shapes get() = mGeometry?.let {
        setOf(IgShapeImpl(it))
    } ?: mShapes.values

    internal inline fun assertThread() = backend.assertThread()

    override fun isAdded() = handle.collisionSpace != null

    override fun setGeometry(geometry: IgGeometry) {
        assertThread()
        mShapes.clear()
        mCompound = null
        mGeometry = geometry
        handle.collisionShape = backend.btShapeOf(geometry)
    }

    override fun attachShape(shape: IgShape) {
        assertThread()
        shape as BltShape

        val compound = mCompound ?: CompoundCollisionShape().also {
            handle.collisionShape?.let { oldShape ->
                it.addChildShape(oldShape)
            }
            mGeometry = null
            mCompound = it
            handle.collisionShape = it
        }
        mShapes[shape.handle.nativeId()] = shape
        compound.addChildShape(shape.handle, shape.transform.btSp())
    }

    override fun detachShape(shape: IgShape) {
        assertThread()
        shape as BltShape
        val compound = mCompound ?: return
        mShapes.remove(shape.handle.nativeId())
        compound.removeChildShape(shape.handle)
    }

    override fun detachAllShapes() {
        assertThread()
        mGeometry = null
        mShapes.clear()
        mCompound = null
        handle.collisionShape = backend.emptyShape
    }

    override fun destroy() {}
}

class BltRigidBody(
    backend: BulletBackend,
    override val handle: PhysicsRigidBody
) : BltBody(backend, handle), IgStaticBody, IgDynamicBody {
    override var linearVelocity: Vec3
        get() {
            assertThread()
            return handle.linearVelocity
        }
        set(value) {
            assertThread()
            handle.linearVelocity = value
        }

    override var angularVelocity: Vec3
        get() {
            assertThread()
            return handle.angularVelocity
        }
        set(value) {
            assertThread()
            handle.angularVelocity = value
        }

    override var kinematic: Boolean
        get() {
            assertThread()
            return handle.isKinematic
        }
        set(value) {
            assertThread()
            handle.isKinematic = value
        }

    override val active: Boolean
        get() {
            assertThread()
            return handle.isActive
        }

    override val boundingBox: AABB
        get() {
            assertThread()
            return handle.boundingBox()
        }

    override fun activate() {
        assertThread()
        handle.activate(true)
    }

    override fun applyForce(force: Vec3) {
        assertThread()
        handle.applyCentralForce(force.btSp())
    }

    override fun applyForceImpulse(force: Vec3) {
        assertThread()
        handle.applyCentralImpulse(force.btSp())
    }

    override fun applyTorque(torque: Vec3) {
        assertThread()
        handle.applyTorque(torque.btSp())
    }

    override fun applyTorqueImpulse(torque: Vec3) {
        assertThread()
        handle.applyTorqueImpulse(torque.btSp())
    }
}
