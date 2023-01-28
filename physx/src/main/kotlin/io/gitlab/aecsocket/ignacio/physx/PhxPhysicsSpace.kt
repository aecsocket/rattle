package io.gitlab.aecsocket.ignacio.physx

import io.gitlab.aecsocket.ignacio.core.*
import io.gitlab.aecsocket.ignacio.core.math.Transform
import io.gitlab.aecsocket.ignacio.core.math.Vec3
import physx.physics.PxActor
import physx.physics.PxOverlapBuffer10
import physx.physics.PxScene
import physx.support.SupportFunctions

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

    val mBodies = HashMap<PxActor, PhxBody>()
    override val bodies: Collection<PhxBody> get() = mBodies.values
    val mAwakeBodies = ArrayList<PhxBody>()
    override val bodiesAwake: Collection<PhxBody> get() = mAwakeBodies

    private val overlapBuffer = PxOverlapBuffer10()

    private inline fun assertThread() = backend.assertThread()

    override fun addBody(body: IgBody) {
        assertThread()
        body as PhxBody
        mBodies[body.handle] = body
        handle.addActor(body.handle)
    }

    override fun removeBody(body: IgBody) {
        assertThread()
        body as PhxBody
        mBodies.remove(body.handle)
        handle.removeActor(body.handle)
    }

    override fun countBodies(onlyAwake: Boolean): Int {
        assertThread()
        return if (onlyAwake) bodiesAwake.size else bodies.size
    }

    override fun nearbyBodies(position: Vec3, radius: IgScalar): List<PhxBody> {
        assertThread()
        val result = ArrayList<PhxBody>()
        igUseMemory {
            // TODO MEGA BIG REALLY BAD: This can only store 10 (TEN) ACTORS!!!!
            // phyxs-jni:physx/source/webidlbindings/src/common/WebIdlBindings.h
            // typedef physx::PxOverlapBufferN<10> PxOverlapBuffer10;
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

            for (i in 0 until overlapBuffer.nbAnyHits) {
                val overlap = overlapBuffer.getAnyHit(i)
                result.add(mBodies[overlap.actor]!!) // TODO also include overlap.shape
            }
        }
        return result
    }

    fun queueStep() {
        handle.simulate(settings.stepInterval.toFloat())
    }

    fun joinStep() {
        handle.fetchResults(true)
        mAwakeBodies.clear()
        val activeActors = SupportFunctions.PxScene_getActiveActors(handle)
        for (i in 0 until activeActors.size()) {
            mAwakeBodies.add(mBodies[activeActors.at(i)]!!)
        }
    }

    internal fun destroy() {
        overlapBuffer.destroy()
        mBodies.forEach { (_, body) ->
            handle.removeActor(body.handle)
            body.handle.release()
        }
        handle.release()
    }
}
