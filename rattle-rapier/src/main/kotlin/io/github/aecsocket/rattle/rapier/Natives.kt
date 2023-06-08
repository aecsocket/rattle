package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.rattle.*
import rapier.Droppable
import rapier.Native
import rapier.dynamics.joint.MotorModel
import rapier.geometry.CoefficientCombineRule
import rapier.geometry.VHACDParameters
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

abstract class RapierNative {
    abstract val handle: Native

    abstract val nativeType: String

    override fun toString() = "$nativeType[0x%x]".format(handle.addr())

    override fun equals(other: Any?) = other is RapierNative && handle == other.handle

    override fun hashCode() = handle.hashCode()
}

abstract class RapierRefCounted : RapierNative(), RefCounted {
    abstract override val handle: rapier.RefCounted

    override val refCount: Long
        get() = handle.strongCount()

    // don't implement so that concrete types return their own types
    abstract override fun acquire(): RapierRefCounted

    abstract override fun release(): RapierRefCounted

    // equality and hashing is done by keying the underlying shape, **not** the ref-counting (Arc) object
    override fun equals(other: Any?) = other is RapierRefCounted && handle.refData() == other.handle.refData()

    override fun hashCode() = handle.refData().hashCode()
}

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

fun VhacdSettings.toParams(alloc: SegmentAllocator) = VHACDParameters.create(alloc).also {
    it.concavity = concavity
    it.alpha = alpha
    it.beta = beta
    it.resolution = resolution
    it.planeDownsampling = planeDownsampling
    it.convexHullDownsampling = convexHullDownsampling
    it.fillMode = when (val fillMode = fillMode) {
        is VhacdSettings.FillMode.SurfaceOnly -> VHACDParameters.FillMode.SurfaceOnly.INSTANCE
        is VhacdSettings.FillMode.FloodFill -> VHACDParameters.FillMode.FloodFill(fillMode.detectCavities)
    }
    it.convexHullApproximation = convexHullApproximation
    it.maxConvexHulls = maxConvexHulls
}

fun InteractionGroup.convert(alloc: SegmentAllocator) = rapier.geometry.InteractionGroups.of(alloc, memberships.raw, filter.raw)

fun rapier.geometry.InteractionGroups.convert() = InteractionGroup(
    memberships = InteractionField.fromRaw(memberships),
    filter = InteractionField.fromRaw(filter),
)

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

fun JointAxis.Motor.Model.convert() = when (this) {
    JointAxis.Motor.Model.ACCELERATION_BASED -> MotorModel.ACCELERATION_BASED
    JointAxis.Motor.Model.FORCE_BASED        -> MotorModel.FORCE_BASED
}

fun MotorModel.convert() = when (this) {
    MotorModel.ACCELERATION_BASED -> JointAxis.Motor.Model.ACCELERATION_BASED
    MotorModel.FORCE_BASED        -> JointAxis.Motor.Model.FORCE_BASED
}