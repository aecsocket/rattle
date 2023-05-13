package io.github.aecsocket.ignacio.rapier

import io.github.aecsocket.ignacio.Iso
import io.github.aecsocket.ignacio.Quat
import io.github.aecsocket.ignacio.Vec
import rapier.math.AngVector
import rapier.math.Isometry
import rapier.math.Rotation
import rapier.math.Vector
import java.lang.foreign.Arena
import java.lang.foreign.SegmentAllocator

fun <R> pushArena(block: (arena: Arena) -> R): R =
    Arena.openConfined().use(block)

fun Vec.asVector(alloc: SegmentAllocator) = Vector.of(alloc, x, y, z)
fun Vector.asVec() = Vec(x, y, z)

fun Vec.asAngVector(alloc: SegmentAllocator) = AngVector.of(alloc, x, y, z)
fun AngVector.asVec() = Vec(x, y, z)

fun Quat.asRotation(alloc: SegmentAllocator) = Rotation.of(alloc, x, y, z, w)
fun Rotation.asQuat() = Quat(x, y, z, w)

fun Iso.asIsometry(alloc: SegmentAllocator) = Isometry.of(alloc, rotation.asRotation(alloc), position.asVector(alloc))
fun Isometry.asIso() = Iso(translation.asVec(), rotation.asQuat())
