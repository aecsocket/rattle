package io.github.aecsocket.ignacio.physx

import io.github.aecsocket.ignacio.*
import physx.common.PxQuat
import physx.common.PxTransform
import physx.common.PxVec3
import java.lang.foreign.Arena
import java.lang.foreign.SegmentAllocator

fun <R> pushArena(block: (arena: Arena) -> R): R =
    Arena.openConfined().use(block)

internal val allocFn: (SegmentAllocator, Int, Int) -> Long = { alloc, alignment, size ->
    alloc.allocate(size.toLong(), alignment.toLong()).address()
}

fun Vec.toPx(alloc: SegmentAllocator) = PxVec3.createAt(alloc, allocFn, x.toFloat(), y.toFloat(), z.toFloat())
fun PxVec3.toVec() = Vec(x.toDouble(), y.toDouble(), z.toDouble())

fun Quat.toPx(alloc: SegmentAllocator) = PxQuat.createAt(alloc, allocFn, x.toFloat(), y.toFloat(), z.toFloat(), w.toFloat())
fun PxQuat.toQuat() = Quat(x.toDouble(), y.toDouble(), z.toDouble(), w.toDouble())

fun Iso.toPx(alloc: SegmentAllocator) = PxTransform.createAt(alloc, allocFn, translation.toPx(alloc), rotation.toPx(alloc))
fun PxTransform.toIso() = Iso(p.toVec(), q.toQuat())
