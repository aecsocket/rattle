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
import rapier.pipeline.ComplexPointProject
import rapier.pipeline.ComplexRayResult
import rapier.pipeline.FeatureId
import rapier.pipeline.SimplePointProject
import rapier.pipeline.SimpleRayResult
import rapier.pipeline.TOI
import rapier.pipeline.TOIStatus
import java.lang.foreign.Addressable
import java.lang.foreign.SegmentAllocator

// TODO Java 20: just use Arena directly
typealias Arena = java.lang.foreign.MemorySession

private typealias RAabb = rapier.math.Aabb
private typealias RRay = rapier.math.Ray

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

fun Ray.toRapier(alloc: SegmentAllocator) = RRay.of(alloc, origin.toVector(alloc), direction.toVector(alloc))
fun RRay.toRay() = Ray(origin.toVec(), dir.toVec())

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

fun QueryFilter.convert(arena: Arena, space: RapierSpace): rapier.pipeline.QueryFilter {
    val filter = rapier.pipeline.QueryFilter.of(
        arena,
        predicate?.let { predicate ->
            { handle, coll ->
                when (predicate(RapierCollider.Read(coll, space), RapierColliderKey(handle))) {
                    QueryResult.CONTINUE -> false
                    QueryResult.STOP -> true
                }
            }
        }
    )
    filter.flags = flags.raw
    filter.groups = group.convert(arena)
    excludeCollider?.let { exclude ->
        exclude as RapierColliderKey
        filter.excludeCollider = exclude.id
    }
    excludeRigidBody?.let { exclude ->
        exclude as RapierRigidBodyKey
        filter.excludeCollider = exclude.id
    }
    return filter
}

fun FeatureId.convert() = when (this) {
    is FeatureId.Vertex -> ShapeFeature.Vertex(id)
    is FeatureId.Edge -> ShapeFeature.Edge(id)
    is FeatureId.Face -> ShapeFeature.Face(id)
    is FeatureId.Unknown -> throw IllegalStateException("Did not expect an Unknown FeatureId")
}

fun TOI.convert(): Intersect = when (val status = status) {
    TOIStatus.PENETRATING -> Intersect.Penetrating
    else -> Intersect.Separated(
        state = when (status) {
            TOIStatus.CONVERGED -> Intersect.State.CONVERGED
            TOIStatus.OUT_OF_ITERATIONS -> Intersect.State.OUT_OF_ITERATIONS
            TOIStatus.FAILED -> Intersect.State.FAILED
            else -> throw IllegalStateException()
        },
        time = toi,
        localPointA = witness1.toVec(),
        localPointB = witness2.toVec(),
        normalA = normal1.toVec(),
        normalB = normal2.toVec(),
    )
}

fun SimpleRayResult.convert() = RayCast.Simple(
    collider = RapierColliderKey(collider),
    hitTime = toi,
)

fun ComplexRayResult.convert() = RayCast.Complex(
    collider = RapierColliderKey(collider),
    hitTime = toi,
    normal = normal.toVec(),
    feature = feature.convert(),
)

fun rapier.pipeline.ShapeCast.convert(): ShapeCast {
    val toi = toi
    return ShapeCast(
        collider = RapierColliderKey(collider),
        intersect = toi.convert(),
    )
}

fun SimplePointProject.convert() = PointProject.Simple(
    collider = RapierColliderKey(collider),
    wasInside = isInside,
    point = point.toVec(),
)

fun ComplexPointProject.convert() = PointProject.Complex(
    collider = RapierColliderKey(collider),
    wasInside = isInside,
    point = point.toVec(),
    feature = feature.convert(),
)
