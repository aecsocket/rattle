package io.gitlab.aecsocket.ignacio.bullet

import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import io.gitlab.aecsocket.ignacio.core.*
import io.gitlab.aecsocket.ignacio.core.math.Transform

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
    private val backend: BulletBackend,
    open val handle: PhysicsCollisionObject
) : IgBody {
    override var transform: Transform
        get() = handle.transform
        set(value) { handle.transform = value }

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

    private inline fun assertThread() = backend.assertThread()

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
        compound.addChildShape(shape.handle)
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
    override val sleeping: Boolean
        get() = !handle.isActive

    override fun wake() {
        handle.activate(true)
    }
}
