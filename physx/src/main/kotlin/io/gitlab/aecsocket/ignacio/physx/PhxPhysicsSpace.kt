package io.gitlab.aecsocket.ignacio.physx

import io.gitlab.aecsocket.ignacio.core.*
import io.gitlab.aecsocket.ignacio.core.math.Transform
import io.gitlab.aecsocket.ignacio.core.math.Vec3
import physx.physics.PxOverlapBuffer10
import physx.physics.PxRigidDynamic
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
            ground.transform = Transform(Vec3(0.0, settings.groundPlaneY, 0.0), groundPlaneQuat)
        }

    val bodies = HashMap<Long, PhxBody>()

    private inline fun assertThread() = backend.assertThread()

    override fun addBody(body: IgBody) {
        assertThread()
        body as PhxBody
        bodies[body.handle.address] = body
        handle.addActor(body.handle)
    }

    override fun removeBody(body: IgBody) {
        assertThread()
        body as PhxBody
        bodies.remove(body.handle.address)
        handle.removeActor(body.handle)
    }

    override fun countBodies(onlyAwake: Boolean): Int {
        assertThread()
        return if (onlyAwake) bodies
            .filter { (_, body) -> (body.handle as? PxRigidDynamic)?.isSleeping == false }
            .size
        else bodies.size
    }

    override fun nearbyBodies(position: Vec3, radius: IgScalar): List<PhxBody> {
        assertThread()
        val result = ArrayList<PhxBody>()
        igUseMemory {
            // TODO MEGA BIG REALLY BAD: This can only store 10 (TEN) ACTORS!!!!
            // phyxs-jni:physx/source/webidlbindings/src/common/WebIdlBindings.h
            // typedef physx::PxOverlapBufferN<10> PxOverlapBuffer10;
            val overlapBuffer = PxOverlapBuffer10()
            val overlaps = handle.overlap(
                pxSphereGeometry(radius.toFloat()),
                pxTransform(pxVec3(position), pxQuat()),
                overlapBuffer,
                pxQueryFilterData(
                    pxFilterData(0, 0, 0, 0),
                    pxQueryFlags(PxQueryFlag.STATIC or PxQueryFlag.DYNAMIC or PxQueryFlag.NO_BLOCK)
                )
            )
            if (!overlaps) return@igUseMemory

            repeat(overlapBuffer.nbAnyHits) {
                val overlap = overlapBuffer.getAnyHit(it)
                result.add(bodies[overlap.actor.address]!!) // TODO also include overlap.shape
            }
        }
        return result
    }
}
