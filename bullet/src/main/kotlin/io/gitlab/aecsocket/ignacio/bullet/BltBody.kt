package io.gitlab.aecsocket.ignacio.bullet

import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsRigidBody
import io.gitlab.aecsocket.ignacio.core.*

open class BltBody(open val handle: PhysicsCollisionObject) : IgBody {
    override var transform: Transform
        get() = handle.transform
        set(value) { handle.transform = value }
}

class BltRigidBody(override val handle: PhysicsRigidBody) : BltBody(handle), IgStaticBody, IgDynamicBody {
}
