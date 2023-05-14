package io.github.aecsocket.ignacio.physx

import io.github.aecsocket.ignacio.*
import physx.NativeObject
import physx.common.PxBaseTask
import physx.physics.PxActorFlagEnum
import physx.physics.PxActorFlags
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

    override fun createCollider(
        shape: Shape,
        material: PhysicsMaterial,
        position: Iso,
        isSensor: Boolean,
    ): Collider {
        shape as PhysxShape
        material as PhysxMaterial
        val pxShape = pushArena { arena ->
            val pxShape = engine.physics.createShape(shape.geom, material.handle)
            pxShape.flags = PxShapeFlags.createAt(
                arena,
                allocFn,
                (PxShapeFlagEnum.eSCENE_QUERY_SHAPE.value or if (isSensor) {
                    PxShapeFlagEnum.eTRIGGER_SHAPE.value
                } else {
                    PxShapeFlagEnum.eSIMULATION_SHAPE.value
                }).toByte(),
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
    ): FixedBodyHandle<VR, VW> {
        val body = pushArena { arena ->
            val body = engine.physics.createRigidStatic(position.toPx(arena))
            body.actorFlags = PxActorFlags.createAt(
                arena,
                allocFn,
                0.toByte(),
            )
            body
        }

        return when (volume) {
            is Volume.Single -> {
                val collider = volume.collider as PhysxCollider
                body.attachShape(collider.shape)

            }
            is Volume.Compound -> {
                volume.colliders.forEach { collider ->
                    collider as PhysxCollider
                    body.attachShape(collider.shape)
                }
            }
        }
    }
}
