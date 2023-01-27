package io.gitlab.aecsocket.ignacio.physx

import io.gitlab.aecsocket.ignacio.core.*
import io.gitlab.aecsocket.ignacio.core.math.Quat
import io.gitlab.aecsocket.ignacio.core.math.Transform
import io.gitlab.aecsocket.ignacio.core.math.Vec3
import org.lwjgl.system.MemoryStack
import physx.common.PxIDENTITYEnum
import physx.common.PxQuat
import physx.common.PxTolerancesScale
import physx.common.PxTransform
import physx.common.PxVec3
import physx.geometry.PxBoxGeometry
import physx.geometry.PxGeometry
import physx.geometry.PxPlaneGeometry
import physx.geometry.PxSphereGeometry
import physx.physics.PxFilterData
import physx.physics.PxQueryFilterData
import physx.physics.PxQueryFlagEnum
import physx.physics.PxQueryFlags
import physx.physics.PxRigidActor
import physx.physics.PxSceneDesc
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

fun PxVec3.ig() = Vec3(x.toDouble(), y.toDouble(), z.toDouble())
fun PxQuat.ig() = Quat(x.toDouble(), y.toDouble(), z.toDouble(), w.toDouble())

fun PxTransform.ig() = Transform(p.ig(), q.ig())

@OptIn(ExperimentalContracts::class)
fun <R> igUseMemory(block: MemoryStack.() -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    return MemoryStack.stackPush().use(block)
}

val PxIdentity inline get() = PxIDENTITYEnum.PxIdentity

object PxQueryFlag {
    val STATIC get() = PxQueryFlagEnum.eSTATIC.value
    val DYNAMIC get() = PxQueryFlagEnum.eDYNAMIC.value
    val PREFILTER get() = PxQueryFlagEnum.ePREFILTER.value
    val POSTFILTER get() = PxQueryFlagEnum.ePOSTFILTER.value
    val ANY_HIT get() = PxQueryFlagEnum.eANY_HIT.value
    val NO_BLOCK get() = PxQueryFlagEnum.eNO_BLOCK.value
}

fun MemoryStack.pxVec3(x: Float, y: Float, z: Float) =
    PxVec3.createAt(this, MemoryStack::nmalloc, x, y, z)
fun MemoryStack.pxVec3(v: Vec3): PxVec3 =
    PxVec3.createAt(this, MemoryStack::nmalloc, v.x.toFloat(), v.y.toFloat(), v.z.toFloat())
fun MemoryStack.pxVec3() =
    PxVec3.createAt(this, MemoryStack::nmalloc, 0f, 0f, 0f)

fun MemoryStack.pxQuat(x: Float, y: Float, z: Float, w: Float) =
    PxQuat.createAt(this, MemoryStack::nmalloc, x, y, z, w)
fun MemoryStack.pxQuat(q: Quat) =
    PxQuat.createAt(this, MemoryStack::nmalloc, q.x.toFloat(), q.y.toFloat(), q.z.toFloat(), q.w.toFloat())
fun MemoryStack.pxQuat() =
    PxQuat.createAt(this, MemoryStack::nmalloc)

fun MemoryStack.pxTransform(v: PxVec3, q: PxQuat) =
    PxTransform.createAt(this, MemoryStack::nmalloc, v, q)
fun MemoryStack.pxTransform(t: Transform) =
    PxTransform.createAt(this, MemoryStack::nmalloc, pxVec3(t.position), pxQuat(t.rotation))
fun MemoryStack.pxTransform() =
    PxTransform.createAt(this, MemoryStack::nmalloc, PxIdentity)

fun MemoryStack.pxSphereGeometry(ir: Float) =
    PxSphereGeometry.createAt(this, MemoryStack::nmalloc, ir)
fun MemoryStack.pxBoxGeometry(hx: Float, hy: Float, hz: Float) =
    PxBoxGeometry.createAt(this, MemoryStack::nmalloc, hx, hy, hz)
fun MemoryStack.pxPlaneGeometry() =
    PxPlaneGeometry.createAt(this, MemoryStack::nmalloc)

fun MemoryStack.pxSceneDesc(scale: PxTolerancesScale) =
    PxSceneDesc.createAt(this, MemoryStack::nmalloc, scale)
fun MemoryStack.pxFilterData(w0: Int, w1: Int, w2: Int, w3: Int) =
    PxFilterData.createAt(this, MemoryStack::nmalloc, w0, w1, w2, w3)
fun MemoryStack.pxQueryFlags(flags: Short) =
    PxQueryFlags.createAt(this, MemoryStack::nmalloc, flags)
fun MemoryStack.pxQueryFlags(flags: Int) =
    PxQueryFlags.createAt(this, MemoryStack::nmalloc, flags.toShort())
fun MemoryStack.pxQueryFilterData(data: PxFilterData, flags: PxQueryFlags) =
    PxQueryFilterData.createAt(this, MemoryStack::nmalloc, data, flags)

var PxRigidActor.transform: Transform
    get() = globalPose.ig()
    set(value) {
        igUseMemory {
            globalPose = pxTransform(value)
        }
    }
