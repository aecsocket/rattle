package io.github.aecsocket.ignacio.physx

import io.github.aecsocket.ignacio.Quat
import io.github.aecsocket.ignacio.Vec
import physx.common.PxQuat
import physx.common.PxVec3
import java.lang.foreign.Arena
import java.lang.foreign.SegmentAllocator

fun <R> pushArena(block: (arena: Arena) -> R): R =
    Arena.openConfined().use(block)

internal val SegmentAllocator.alloc: (SegmentAllocator, Int, Int) -> Long
    get() = { _, alignment, size ->
        allocate(size.toLong(), alignment.toLong()).address()
    }

fun Vec.asNative(alloc: SegmentAllocator) = PxVec3.createAt(alloc, alloc.alloc, x.toFloat(), y.toFloat(), z.toFloat())
fun PxVec3.asVec() = Vec(x.toDouble(), y.toDouble(), z.toDouble())

fun Quat.asRotation(alloc: SegmentAllocator) = PxQuat.createAt(alloc, alloc.alloc, x.toFloat(), y.toFloat(), z.toFloat(), w.toFloat())
fun PxQuat.asQuat() = Quat(x.toDouble(), y.toDouble(), z.toDouble(), w.toDouble())
