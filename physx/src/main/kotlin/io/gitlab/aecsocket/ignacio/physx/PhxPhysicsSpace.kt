package io.gitlab.aecsocket.ignacio.physx

import io.gitlab.aecsocket.ignacio.core.IgBody
import io.gitlab.aecsocket.ignacio.core.IgPhysicsSpace
import physx.physics.PxScene

class PhxPhysicsSpace(val handle: PxScene) : IgPhysicsSpace {
    override fun addBody(body: IgBody) {
        handle.addActor((body as PhxBody).handle)
    }

    override fun destroy() {
        handle.release()
    }
}
