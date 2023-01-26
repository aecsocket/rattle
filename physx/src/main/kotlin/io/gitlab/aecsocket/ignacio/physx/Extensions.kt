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

fun MemoryStack.pxVec3(x: Float, y: Float, z: Float) =
    PxVec3.createAt(this, MemoryStack::nmalloc, x, y, z)
fun MemoryStack.pxVec3(vec: Vec3): PxVec3 =
    PxVec3.createAt(this, MemoryStack::nmalloc, vec.x.toFloat(), vec.y.toFloat(), vec.z.toFloat())
fun MemoryStack.pxVec3() =
    PxVec3.createAt(this, MemoryStack::nmalloc, 0f, 0f, 0f)

fun MemoryStack.pxQuat(x: Float, y: Float, z: Float, w: Float) =
    PxQuat.createAt(this, MemoryStack::nmalloc, x, y, z, w)
fun MemoryStack.pxQuat(quat: Quat) =
    PxQuat.createAt(this, MemoryStack::nmalloc, quat.x.toFloat(), quat.y.toFloat(), quat.z.toFloat(), quat.w.toFloat())
fun MemoryStack.pxQuat() =
    PxQuat.createAt(this, MemoryStack::nmalloc)

fun MemoryStack.pxTransform(vec: PxVec3, quat: PxQuat) =
    PxTransform.createAt(this, MemoryStack::nmalloc, vec, quat)
fun MemoryStack.pxTransform(tf: Transform) =
    PxTransform.createAt(this, MemoryStack::nmalloc, pxVec3(tf.position), pxQuat(tf.rotation))
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

fun MemoryStack.pxGeometryOf(shape: IgShape): PxGeometry {
    return when (shape) {
        is IgSphereShape -> pxSphereGeometry(shape.radius.toFloat())
        is IgBoxShape -> {
            val (hx, hy, hz) = shape.halfExtent
            pxBoxGeometry(hx.toFloat(), hy.toFloat(), hz.toFloat())
        }
        is IgPlaneShape -> pxPlaneGeometry()
    }
}

var PxRigidActor.transform: Transform
    get() = globalPose.ig()
    set(value) {
        igUseMemory {
            globalPose = pxTransform(value)
        }
    }
