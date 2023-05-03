package io.github.aecsocket.ignacio

import io.github.aecsocket.klam.DVec3
import io.github.aecsocket.klam.FVec2
import io.github.aecsocket.klam.FVec3
import io.github.aecsocket.klam.PI_F

sealed interface ConstraintTarget {
    object World : ConstraintTarget
}

sealed interface ConstraintDescriptor

sealed interface JointDescriptor : ConstraintDescriptor {
    val pointA: DVec3
    val pointB: DVec3
}

data class FixedJointDescriptor(
    override val pointA: DVec3 = DVec3.Zero,
    val axisXA: FVec3 = FVec3.X,
    val axisYA: FVec3 = FVec3.Y,
    override val pointB: DVec3 = DVec3.Zero,
    val axisXB: FVec3 = FVec3.X,
    val axisYB: FVec3 = FVec3.Y,
) : JointDescriptor

data class PointJointDescriptor(
    override val pointA: DVec3 = DVec3.Zero,
    override val pointB: DVec3 = DVec3.Zero,
) : JointDescriptor

data class HingeJointDescriptor(
    override val pointA: DVec3 = DVec3.Zero,
    val hingeAxisA: FVec3 = FVec3.Y,
    val normalAxisA: FVec3 = FVec3.X,
    override val pointB: DVec3 = DVec3.Zero,
    val hingeAxisB: FVec3 = FVec3.Y,
    val normalAxisB: FVec3 = FVec3.X,
    val limits: FVec2 = FVec2(-PI_F, PI_F),
    val maxFrictionTorque: Float = 0.0f,
) : JointDescriptor

data class SliderJointDescriptor(
    override val pointA: DVec3 = DVec3.Zero,
    val sliderAxisA: FVec3 = FVec3.X,
    val normalAxisA: FVec3 = FVec3.Y,
    override val pointB: DVec3 = DVec3.Zero,
    val sliderAxisB: FVec3 = FVec3.X,
    val normalAxisB: FVec3 = FVec3.Y,
    val limits: FVec2 = FVec2(Float.MIN_VALUE, Float.MAX_VALUE),
    val frequency: Float = 0.0f,
    val damping: Float = 0.0f,
    val maxFrictionForce: Float = 0.0f,
) : JointDescriptor

data class DistanceJointDescriptor(
    override val pointA: DVec3 = DVec3.Zero,
    override val pointB: DVec3 = DVec3.Zero,
    val limits: FVec2 = FVec2.Zero,
    val frequency: Float = 0.0f,
    val damping: Float = 0.0f,
) : JointDescriptor

data class ConeJointDescriptor(
    override val pointA: DVec3 = DVec3.Zero,
    val twistAxisA: FVec3 = FVec3.X,
    override val pointB: DVec3 = DVec3.Zero,
    val twistAxisB: FVec3 = FVec3.X,
    val halfConeAngle: Float = 0.0f,
) : JointDescriptor

data class SwingTwistJointDescriptor(
    override val pointA: DVec3 = DVec3.Zero,
    val twistAxisA: FVec3 = FVec3.X,
    val planeAxisA: FVec3 = FVec3.Y,
    override val pointB: DVec3 = DVec3.Zero,
    val twistAxisB: FVec3 = FVec3.X,
    val planeAxisB: FVec3 = FVec3.Y,
    val normalHalfConeAngle: Float = 0.0f,
    val planeHalfConeAngle: Float = 0.0f,
    val twistLimits: FVec2 = FVec2.Zero,
    val maxFrictionTorque: Float = 0.0f,
) : JointDescriptor

data class SixDOFJointDescriptor(
    override val pointA: DVec3 = DVec3.Zero,
    val axisXA: FVec3 = FVec3.X,
    val axisYA: FVec3 = FVec3.Y,
    override val pointB: DVec3 = DVec3.Zero,
    val axisXB: FVec3 = FVec3.X,
    val axisYB: FVec3 = FVec3.Y,
) : JointDescriptor

interface Constraint {
    var isEnabled: Boolean
}
