package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.klam.*
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

fun <T : Droppable, R> T.use(block: (T) -> R): R {
    val res = block(this)
    drop()
    return res
}

fun DVec3.toVector() = Vector(x, y, z)
fun DVec3.toAngVector() = AngVector(x, y, z)
fun Vector.toVec() = DVec3(x, y, z)
fun AngVector.toVec() = DVec3(x, y, z)

fun DQuat.toRotation() = Rotation(x, y, z, w)
fun Rotation.toQuat() = DQuat(x, y, z, w)

fun DIso3.toIsometry() = Isometry(rotation.toRotation(), translation.toVector())
fun Isometry.toIso() = DIso3(translation.toVec(), rotation.toQuat())

fun DAabb3.toRapier() = RAabb(min.toVector(), max.toVector())
fun RAabb.toAabb() = DAabb3(min.toVec(), max.toVec())

fun DRay3.toRapier() = RRay(origin.toVector(), direction.toVector())
fun RRay.toRay() = DRay3(origin.toVec(), dir.toVec())

fun VhacdSettings.toParams() = VHACDParameters(
    concavity,
    alpha,
    beta,
    resolution,
    planeDownsampling,
    convexHullDownsampling,
    when (val fillMode = fillMode) {
        is VhacdSettings.FillMode.SurfaceOnly -> VHACDParameters.FillMode.SurfaceOnly.INSTANCE
        is VhacdSettings.FillMode.FloodFill -> VHACDParameters.FillMode.FloodFill(fillMode.detectCavities)
    },
    convexHullApproximation,
    maxConvexHulls,
)

fun InteractionGroup.toRapier() = rapier.geometry.InteractionGroups(memberships.raw, filter.raw)

fun rapier.geometry.InteractionGroups.toRattle() = InteractionGroup(
    memberships = InteractionField.fromRaw(memberships),
    filter = InteractionField.fromRaw(filter),
)

fun RigidBodyType.toRapier() = when (this) {
    RigidBodyType.FIXED     -> rapier.dynamics.RigidBodyType.FIXED
    RigidBodyType.DYNAMIC   -> rapier.dynamics.RigidBodyType.DYNAMIC
    RigidBodyType.KINEMATIC -> rapier.dynamics.RigidBodyType.KINEMATIC_POSITION_BASED
}

fun rapier.dynamics.RigidBodyType.toRattle() = when (this) {
    rapier.dynamics.RigidBodyType.FIXED                    -> RigidBodyType.FIXED
    rapier.dynamics.RigidBodyType.DYNAMIC                  -> RigidBodyType.DYNAMIC
    rapier.dynamics.RigidBodyType.KINEMATIC_POSITION_BASED -> RigidBodyType.KINEMATIC
    rapier.dynamics.RigidBodyType.KINEMATIC_VELOCITY_BASED -> RigidBodyType.KINEMATIC
}

fun CoeffCombineRule.toRapier() = when (this) {
    CoeffCombineRule.AVERAGE  -> CoefficientCombineRule.AVERAGE
    CoeffCombineRule.MIN      -> CoefficientCombineRule.MIN
    CoeffCombineRule.MULTIPLY -> CoefficientCombineRule.MULTIPLY
    CoeffCombineRule.MAX      -> CoefficientCombineRule.MAX
}
fun CoefficientCombineRule.toRattle() = when (this) {
    CoefficientCombineRule.AVERAGE  -> CoeffCombineRule.AVERAGE
    CoefficientCombineRule.MIN      -> CoeffCombineRule.MIN
    CoefficientCombineRule.MULTIPLY -> CoeffCombineRule.MULTIPLY
    CoefficientCombineRule.MAX      -> CoeffCombineRule.MAX
}

fun JointAxis.Motor.Model.toRapier() = when (this) {
    JointAxis.Motor.Model.ACCELERATION_BASED -> MotorModel.ACCELERATION_BASED
    JointAxis.Motor.Model.FORCE_BASED        -> MotorModel.FORCE_BASED
}

fun MotorModel.toRattle() = when (this) {
    MotorModel.ACCELERATION_BASED -> JointAxis.Motor.Model.ACCELERATION_BASED
    MotorModel.FORCE_BASED        -> JointAxis.Motor.Model.FORCE_BASED
}

fun QueryFilter.toRapier(arena: Arena, space: RapierSpace): rapier.pipeline.QueryFilter {
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
    filter.groups = group.toRapier()
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

fun FeatureId.toRattle() = when (this) {
    is FeatureId.Vertex -> ShapeFeature.Vertex(id)
    is FeatureId.Edge -> ShapeFeature.Edge(id)
    is FeatureId.Face -> ShapeFeature.Face(id)
    is FeatureId.Unknown -> throw IllegalStateException("Did not expect an Unknown FeatureId")
}

fun TOI.toRattle(): Intersect = when (val status = status) {
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

fun SimpleRayResult.toRattle() = RayCast.Simple(
    collider = RapierColliderKey(collider),
    hitTime = toi,
)

fun ComplexRayResult.toRattle() = RayCast.Complex(
    collider = RapierColliderKey(collider),
    hitTime = toi,
    normal = normal.toVec(),
    feature = feature.toRattle(),
)

fun rapier.pipeline.ShapeCast.toRattle(): ShapeCast {
    val toi = toi
    return ShapeCast(
        collider = RapierColliderKey(collider),
        intersect = toi.toRattle(),
    )
}

fun SimplePointProject.toRattle() = PointProject.Simple(
    collider = RapierColliderKey(collider),
    wasInside = isInside,
    point = point.toVec(),
)

fun ComplexPointProject.toRattle() = PointProject.Complex(
    collider = RapierColliderKey(collider),
    wasInside = isInside,
    point = point.toVec(),
    feature = feature.toRattle(),
)
