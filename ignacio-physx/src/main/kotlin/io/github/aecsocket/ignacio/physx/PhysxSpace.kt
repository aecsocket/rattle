package io.github.aecsocket.ignacio.physx

import io.github.aecsocket.ignacio.*
import physx.NativeObject
import physx.common.PxBaseTask
import physx.physics.PxActorFlagEnum
import physx.physics.PxActorFlags
import physx.physics.PxRigidBodyFlagEnum
import physx.physics.PxRigidBodyFlags
import physx.physics.PxScene
import physx.physics.PxShapeFlagEnum
import physx.physics.PxShapeFlags
import java.lang.foreign.Arena
import kotlin.reflect.jvm.isAccessible

class PhysxSpace internal constructor(
    val engine: PhysxEngine,
    val handle: PxScene,
    settings: PhysicsSpace.Settings,
) : PhysicsSpace {
    private val destroyed = DestroyFlag()
    private val arena: Arena = Arena.openShared()

    // scratch buffer's size must be a multiple of 16K, and it must be 16-byte aligned
    private val scratchSize = engine.settings.scratchBlocks * 16 * 1024
    private val scratch = NativeObject.wrapPointer(arena.allocate(scratchSize.toLong(), 16).address())

    // stupid hack to make a PxBaseTask pointing to 0x0
    // since we can't pass a null by ourselves ffs
    private val nullTask = PxBaseTask::class.constructors.first().apply {
        isAccessible = true
    }.call(0L)

    override var settings = settings
        set(value) = pushArena { arena ->
            field = value
            handle.gravity = value.gravity.toPx(arena)
        }

    override fun destroy() {
        destroyed()
        handle.release()
        arena.close()
    }

    override fun startStep(dt: Real) {
        handle.simulate(dt.toFloat(), nullTask, scratch, scratchSize)
    }

    override fun finishStep() {
        handle.fetchResults(true)
    }

    override fun addCollider(
        shape: Shape,
        material: PhysicsMaterial,
        position: Iso,
        isSensor: Boolean,
    ): PhysxCollider {
        shape as PhysxShape
        material as PhysxMaterial
        val pxShape = pushArena { arena ->
            val pxShape = engine.physics.createShape(shape.geom, material.handle)
            pxShape.flags = PxShapeFlags.createAt(
                arena,
                allocFn,
                (
                    PxShapeFlagEnum.eSCENE_QUERY_SHAPE.value or
                    (if (isSensor) PxShapeFlagEnum.eTRIGGER_SHAPE.value else PxShapeFlagEnum.eSIMULATION_SHAPE.value)
                ).toByte()
            )
            pxShape.simulationFilterData = engine.defaultFilterData
            pxShape.localPose = position.toPx(arena)
            pxShape
        }
        return PhysxCollider(pxShape)
    }

    override fun <VR : VolumeAccess, VW : VR> addFixedBody(
        position: Iso,
        volume: Volume<VR, VW>
    ): PhysxFixed<VR, VW> {
        val body = pushArena { arena ->
            val body = engine.physics.createRigidStatic(position.toPx(arena))

            body.actorFlags = PxActorFlags.createAt(arena, allocFn, 0.toByte())
            body
        }

        return PhysxFixed(body, volumeAccessOf(body, volume))
    }

    override fun <VR : VolumeAccess, VW : VR> addMovingBody(
        position: Iso,
        volume: Volume<VR, VW>,
        isKinematic: Boolean,
        linearVelocity: Vec,
        angularVelocity: Vec,
        gravity: Gravity,
        linearDamping: Real,
        angularDamping: Real,
        isCcdEnabled: Boolean,
        canSleep: Boolean,
        isSleeping: Boolean,
    ): PhysxMoving<VR, VW> {
        val body = pushArena { arena ->
            val body = engine.physics.createRigidDynamic(position.toPx(arena))
            body.linearVelocity = linearVelocity.toPx(arena)
            body.angularVelocity = angularVelocity.toPx(arena)
            body.linearDamping = linearDamping.toFloat()
            body.angularDamping = angularDamping.toFloat()
            if (!canSleep) body.sleepThreshold = Float.MAX_VALUE
            if (isSleeping) body.putToSleep()
            else body.wakeUp()

            var actorFlags = 0
            val rigidBodyFlags =
                (if (isKinematic) PxRigidBodyFlagEnum.eKINEMATIC.value else 0) or
                (if (isCcdEnabled) PxRigidBodyFlagEnum.eENABLE_CCD.value else 0)

            when (gravity) {
                is Gravity.Enabled -> {}
                is Gravity.Disabled -> actorFlags = PxActorFlagEnum.eDISABLE_GRAVITY.value
                is Gravity.Scaled -> {
                    // fallback to Gravity.Enabled
                    engine.unsupported("PhysxSpace.addMovingBody - Gravity.Scale for `gravity` is unsupported")
                }
            }

            body.actorFlags = PxActorFlags.createAt(arena, allocFn, actorFlags.toByte())
            body.rigidBodyFlags = PxRigidBodyFlags.createAt(arena, allocFn, rigidBodyFlags.toByte())
            body
        }
        return PhysxMoving(body, volumeAccessOf(body, volume))
    }
}
