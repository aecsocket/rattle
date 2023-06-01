package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.rattle.*
import rapier.Droppable
import rapier.Native
import rapier.geometry.CoefficientCombineRule
import rapier.math.AngVector
import rapier.math.Isometry
import rapier.math.Rotation
import rapier.math.Vector
import java.lang.foreign.Addressable
import java.lang.foreign.SegmentAllocator

// TODO Java 20: just use Arena directly
typealias Arena = java.lang.foreign.MemorySession

typealias RAabb = rapier.math.Aabb

fun Addressable.addr() = address().toRawLongValue()

fun Native.addr() = memory().addr()

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

fun Aabb.toRapier(alloc: SegmentAllocator) = RAabb.of(alloc, min.toVector(alloc), max.toVector(alloc))
fun RAabb.toAabb() = Aabb(min.toVec(), max.toVec())

fun RigidBodyType.convert() = when (this) {
    RigidBodyType.FIXED     -> rapier.dynamics.RigidBodyType.FIXED
    RigidBodyType.DYNAMIC   -> rapier.dynamics.RigidBodyType.DYNAMIC
    RigidBodyType.KINEMATIC -> rapier.dynamics.RigidBodyType.KINEMATIC_POSITION_BASED
}

fun rapier.dynamics.RigidBodyType.convert() = when (this) {
    rapier.dynamics.RigidBodyType.FIXED                    -> RigidBodyType.FIXED
    rapier.dynamics.RigidBodyType.DYNAMIC                  -> RigidBodyType.DYNAMIC
    rapier.dynamics.RigidBodyType.KINEMATIC_POSITION_BASED -> RigidBodyType.KINEMATIC
    rapier.dynamics.RigidBodyType.KINEMATIC_VELOCITY_BASED -> RigidBodyType.KINEMATIC
}

fun CoeffCombineRule.convert() = when (this) {
    CoeffCombineRule.AVERAGE  -> CoefficientCombineRule.AVERAGE
    CoeffCombineRule.MIN      -> CoefficientCombineRule.MIN
    CoeffCombineRule.MULTIPLY -> CoefficientCombineRule.MULTIPLY
    CoeffCombineRule.MAX      -> CoefficientCombineRule.MAX
}
fun CoefficientCombineRule.convert() = when (this) {
    CoefficientCombineRule.AVERAGE  -> CoeffCombineRule.AVERAGE
    CoefficientCombineRule.MIN      -> CoeffCombineRule.MIN
    CoefficientCombineRule.MULTIPLY -> CoeffCombineRule.MULTIPLY
    CoefficientCombineRule.MAX      -> CoeffCombineRule.MAX
}

fun JointAxis.convert() = when (this) {
    JointAxis.X     -> rapier.dynamics.joint.JointAxis.X
    JointAxis.Y     -> rapier.dynamics.joint.JointAxis.Y
    JointAxis.Z     -> rapier.dynamics.joint.JointAxis.Z
    JointAxis.ANG_X -> rapier.dynamics.joint.JointAxis.ANG_X
    JointAxis.ANG_Y -> rapier.dynamics.joint.JointAxis.ANG_Y
    JointAxis.ANG_Z -> rapier.dynamics.joint.JointAxis.ANG_Z
}
