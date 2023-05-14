package io.github.aecsocket.ignacio.physx

import io.github.aecsocket.ignacio.Iso
import io.github.aecsocket.ignacio.Quat
import io.github.aecsocket.ignacio.Vec
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

fun Vec.asPx(alloc: SegmentAllocator) = PxVec3.createAt(alloc, allocFn, x.toFloat(), y.toFloat(), z.toFloat())
fun PxVec3.asVec() = Vec(x.toDouble(), y.toDouble(), z.toDouble())

fun Quat.asPx(alloc: SegmentAllocator) = PxQuat.createAt(alloc, allocFn, x.toFloat(), y.toFloat(), z.toFloat(), w.toFloat())
fun PxQuat.asQuat() = Quat(x.toDouble(), y.toDouble(), z.toDouble(), w.toDouble())

fun Iso.asPx(alloc: SegmentAllocator) = PxTransform.createAt(alloc, allocFn, translation.asPx(alloc), rotation.asPx(alloc))
fun PxTransform.asIso() = Iso(p.asVec(), q.asQuat())
