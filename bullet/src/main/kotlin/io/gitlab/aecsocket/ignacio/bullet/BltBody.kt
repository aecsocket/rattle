package io.gitlab.aecsocket.ignacio.bullet

import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsRigidBody
import io.gitlab.aecsocket.ignacio.core.*
import io.gitlab.aecsocket.ignacio.core.math.Transform

open class BltBody(open val handle: PhysicsCollisionObject) : IgBody {
    override var transform: Transform
        get() = handle.transform
        set(value) { handle.transform = value }

    override fun destroy() {}
}

class BltRigidBody(override val handle: PhysicsRigidBody) : BltBody(handle), IgStaticBody, IgDynamicBody {
    override val sleeping: Boolean
        get() = !handle.isActive

    override fun wake() {
        handle.activate(true)
    }
}
