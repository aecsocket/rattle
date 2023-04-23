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
    override val pointA: DVec3 = DVec3(0.0, 0.0, 0.0),
    val axisXA: FVec3 = FVec3(1.0f, 0.0f, 0.0f),
    val axisYA: FVec3 = FVec3(0.0f, 1.0f, 0.0f),
    override val pointB: DVec3 = DVec3(0.0, 0.0, 0.0),
    val axisXB: FVec3 = FVec3(1.0f, 0.0f, 0.0f),
    val axisYB: FVec3 = FVec3(0.0f, 1.0f, 0.0f),
) : JointDescriptor

data class PointJointDescriptor(
    override val pointA: DVec3 = DVec3(0.0, 0.0, 0.0),
    override val pointB: DVec3 = DVec3(0.0, 0.0, 0.0),
) : JointDescriptor

data class HingeJointDescriptor(
    override val pointA: DVec3 = DVec3(0.0, 0.0, 0.0),
    val hingeAxisA: FVec3 = FVec3(0.0f, 1.0f, 0.0f),
    val normalAxisA: FVec3 = FVec3(1.0f, 0.0f, 0.0f),
    override val pointB: DVec3 = DVec3(0.0, 0.0, 0.0),
    val hingeAxisB: FVec3 = FVec3(0.0f, 1.0f, 0.0f),
    val normalAxisB: FVec3 = FVec3(1.0f, 0.0f, 0.0f),
    val limits: FVec2 = FVec2(-PI_F, PI_F),
    val maxFrictionTorque: Float = 0.0f,
) : JointDescriptor

data class SliderJointDescriptor(
    override val pointA: DVec3 = DVec3(0.0, 0.0, 0.0),
    val sliderAxisA: FVec3 = FVec3(1.0f, 0.0f, 0.0f),
    val normalAxisA: FVec3 = FVec3(0.0f, 1.0f, 0.0f),
    override val pointB: DVec3 = DVec3(0.0, 0.0, 0.0),
    val sliderAxisB: FVec3 = FVec3(1.0f, 0.0f, 0.0f),
    val normalAxisB: FVec3 = FVec3(0.0f, 1.0f, 0.0f),
    val limits: FVec2 = FVec2(Float.MIN_VALUE, Float.MAX_VALUE),
    val frequency: Float = 0.0f,
    val damping: Float = 0.0f,
    val maxFrictionForce: Float = 0.0f,
) : JointDescriptor

data class DistanceJointDescriptor(
    override val pointA: DVec3 = DVec3(0.0, 0.0, 0.0),
    override val pointB: DVec3 = DVec3(0.0, 0.0, 0.0),
    val limits: FVec2 = FVec2(0.0f, 0.0f),
    val frequency: Float = 0.0f,
    val damping: Float = 0.0f,
) : JointDescriptor

data class ConeJointDescriptor(
    override val pointA: DVec3 = DVec3(0.0, 0.0, 0.0),
    val twistAxisA: FVec3 = FVec3(1.0f, 0.0f, 0.0f),
    override val pointB: DVec3 = DVec3(0.0, 0.0, 0.0),
    val twistAxisB: FVec3 = FVec3(1.0f, 0.0f, 0.0f),
    val halfConeAngle: Float = 0.0f,
) : JointDescriptor

data class SwingTwistJointDescriptor(
    override val pointA: DVec3 = DVec3(0.0, 0.0, 0.0),
    val twistAxisA: FVec3 = FVec3(1.0f, 0.0f, 0.0f),
    val planeAxisA: FVec3 = FVec3(0.0f, 1.0f, 0.0f),
    override val pointB: DVec3 = DVec3(0.0, 0.0, 0.0),
    val twistAxisB: FVec3 = FVec3(1.0f, 0.0f, 0.0f),
    val planeAxisB: FVec3 = FVec3(0.0f, 1.0f, 0.0f),
    val normalHalfConeAngle: Float = 0.0f,
    val planeHalfConeAngle: Float = 0.0f,
    val twistLimits: FVec2 = FVec2(0.0f, 0.0f),
    val maxFrictionTorque: Float = 0.0f,
) : JointDescriptor

data class SixDOFJointDescriptor(
    override val pointA: DVec3 = DVec3(0.0, 0.0, 0.0),
    val axisXA: FVec3 = FVec3(1.0f, 0.0f, 0.0f),
    val axisYA: FVec3 = FVec3(0.0f, 1.0f, 0.0f),
    override val pointB: DVec3 = DVec3(0.0, 0.0, 0.0),
    val axisXB: FVec3 = FVec3(1.0f, 0.0f, 0.0f),
    val axisYB: FVec3 = FVec3(0.0f, 1.0f, 0.0f),
) : JointDescriptor

interface Constraint {
    var isEnabled: Boolean
}
