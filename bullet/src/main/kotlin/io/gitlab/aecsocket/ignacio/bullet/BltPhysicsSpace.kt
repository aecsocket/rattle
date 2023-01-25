package io.gitlab.aecsocket.ignacio.bullet

import com.jme3.bullet.PhysicsSpace
import io.gitlab.aecsocket.ignacio.core.IgBody
import io.gitlab.aecsocket.ignacio.core.IgPhysicsSpace

class BltPhysicsSpace(val handle: PhysicsSpace) : IgPhysicsSpace {
    override fun addBody(body: IgBody) {
        handle.addCollisionObject((body as BltRigidBody).handle)
    }

    override fun destroy() {
        handle.destroy()
    }
}
