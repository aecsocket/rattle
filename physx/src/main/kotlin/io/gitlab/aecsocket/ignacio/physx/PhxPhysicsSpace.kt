package io.gitlab.aecsocket.ignacio.physx

import io.gitlab.aecsocket.ignacio.core.*
import io.gitlab.aecsocket.ignacio.core.math.Quat
import io.gitlab.aecsocket.ignacio.core.math.Transform
import io.gitlab.aecsocket.ignacio.core.math.Vec3
import physx.physics.PxScene

class PhxPhysicsSpace(
    private val backend: PhysxBackend,
    val handle: PxScene,
    settings: IgPhysicsSpace.Settings,
    val ground: PhxStaticBody
) : IgPhysicsSpace {
    override var settings = settings
        set(value) {
            field = value
            ground.transform = Transform(Vec3(0.0, settings.groundPlaneY, 0.0), Quat.Identity)
        }

    val bodies = HashMap<Long, PhxBody>()

    override fun step() {
        handle.simulate(settings.stepInterval.toFloat())
        handle.fetchResults(true)
    }

    override fun addBody(body: IgBody) {
        body as PhxBody
        bodies[body.handle.address] = body
        handle.addActor(body.handle)
    }

    override fun removeBody(body: IgBody) {
        body as PhxBody
        bodies.remove(body.handle.address)
    }
}
