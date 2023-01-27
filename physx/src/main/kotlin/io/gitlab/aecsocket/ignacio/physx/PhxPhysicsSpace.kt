package io.gitlab.aecsocket.ignacio.physx

import io.gitlab.aecsocket.ignacio.core.*
import io.gitlab.aecsocket.ignacio.core.math.Quat
import io.gitlab.aecsocket.ignacio.core.math.Transform
import io.gitlab.aecsocket.ignacio.core.math.Vec3
import io.gitlab.aecsocket.ignacio.core.util.TimedCache
import io.gitlab.aecsocket.ignacio.core.util.timeNanos
import physx.physics.PxRigidDynamic
import physx.physics.PxScene
import kotlin.math.sqrt

internal val planeQuat = Quat(0.0, 0.0, 1 / sqrt(2.0), 1 / sqrt(2.0))

class PhxPhysicsSpace(
    private val backend: PhysxBackend,
    val handle: PxScene,
    settings: IgPhysicsSpace.Settings,
    val ground: PhxStaticBody
) : IgPhysicsSpace {
    override var settings = settings
        set(value) {
            field = value
            ground.transform = Transform(Vec3(0.0, settings.groundPlaneY, 0.0), planeQuat)
        }

    val bodies = HashMap<Long, PhxBody>()

    private inline fun assertThread() = backend.assertThread()

    override fun addBody(body: IgBody) {
        assertThread()
        body as PhxBody
        this.bodies[body.handle.address] = body
        handle.addActor(body.handle)
    }

    override fun removeBody(body: IgBody) {
        assertThread()
        body as PhxBody
        this.bodies.remove(body.handle.address)
        handle.removeActor(body.handle)
    }

    override fun countBodies(onlyAwake: Boolean): Int {
        assertThread()
        return if (onlyAwake) bodies
            .filter { (_, body) -> (body.handle as? PxRigidDynamic)?.isSleeping == false }
            .size
        else bodies.size
    }
}
