package io.github.aecsocket.ignacio.rapier

import io.github.aecsocket.ignacio.Iso
import io.github.aecsocket.ignacio.Quat
import io.github.aecsocket.ignacio.Vec
import rapier.Droppable
import rapier.Native
import rapier.math.AngVector
import rapier.math.Isometry
import rapier.math.Rotation
import rapier.math.Vector
import java.lang.foreign.Arena
import java.lang.foreign.SegmentAllocator

fun <R> pushArena(block: (arena: Arena) -> R): R =
    Arena.openConfined().use(block)

fun <T : Droppable, R> T.use(block: (T) -> R): R {
    val res = block(this)
    drop()
    return res
}

fun Vec.toVector(alloc: SegmentAllocator) = Vector.of(alloc, x, y, z)
fun Vector.toVec() = Vec(x, y, z)
fun Vector.copyFrom(v: Vec) {
    x = v.x
    y = v.y
    z = v.z
}

fun Vec.toAngVector(alloc: SegmentAllocator) = AngVector.of(alloc, x, y, z)
fun AngVector.toVec() = Vec(x, y, z)

fun Quat.toRotation(alloc: SegmentAllocator) = Rotation.of(alloc, x, y, z, w)
fun Rotation.toQuat() = Quat(x, y, z, w)

fun Iso.toIsometry(alloc: SegmentAllocator) = Isometry.of(alloc, rotation.toRotation(alloc), translation.toVector(alloc))
fun Isometry.toIso() = Iso(translation.toVec(), rotation.toQuat())
