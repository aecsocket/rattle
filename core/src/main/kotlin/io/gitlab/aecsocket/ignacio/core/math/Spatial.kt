package io.gitlab.aecsocket.ignacio.core.math

import kotlin.math.*

private const val FPI = PI.toFloat()
internal const val DECIMAL_FORMAT = "%.3f"
const val EPSILON = 0.000001
const val ONE_EPSILON = 0.999999

fun sqr(x: Float) = x*x
fun sqr(x: Double) = x*x
fun clamp(value: Double, min: Double, max: Double) = min(max, max(min, value))
fun clamp(value: Float, min: Float, max: Float) = min(max, max(min, value))
fun clamp(value: Int, min: Int, max: Int) = min(max, max(min, value))
fun clamp(value: Long, min: Long, max: Long) = min(max, max(min, value))
fun clamp01(value: Double) = clamp(value, 0.0, 1.0)
fun clamp01(value: Float) = clamp(value, 0f, 1f)

fun radians(x: Float) = x * (FPI / 180)
fun radians(x: Double) = x * (PI / 180)
fun degrees(x: Float) = x * (180 / FPI)
fun degrees(x: Double) = x * (180 / PI)

fun Vec3.radians() = Vec3(radians(x), radians(y), radians(z))
fun Vec3.degrees() = Vec3(degrees(x), degrees(y), degrees(z))

enum class EulerOrder {
    XYZ,
    YXZ,
    ZXY,
    ZYX,
    YZX,
    XZY
}

// most math code taken from
// https://github.com/mrdoob/three.js/tree/dev/src/math/

fun Vec3.quat(order: EulerOrder): Quat {
    val s1 = sin(x / 2); val c1 = cos(x / 2)
    val s2 = sin(y / 2); val c2 = cos(y / 2)
    val s3 = sin(z / 2); val c3 = cos(z / 2)

    return when (order) {
        EulerOrder.XYZ -> Quat(
            s1*c2*c3 + c1*s2*s3,
            c1*s2*c3 - s1*c2*s3,
            c1*c2*s3 + s1*s2*c3,
            c1*c2*c3 - s1*s2*s3
        )
        EulerOrder.YXZ -> Quat(
            s1*c2*c3 + c1*s2*s3,
            c1*s2*c3 - s1*c2*s3,
            c1*c2*s3 - s1*s2*c3,
            c1*c2*c3 + s1*s2*s3
        )
        EulerOrder.ZXY -> Quat(
            s1*c2*c3 - c1*s2*s3,
            c1*s2*c3 + s1*c2*s3,
            c1*c2*s3 + s1*s2*c3,
            c1*c2*c3 - s1*s2*s3
        )
        EulerOrder.ZYX -> Quat(
            s1*c2*c3 - c1*s2*s3,
            c1*s2*c3 + s1*c2*s3,
            c1*c2*s3 - s1*s2*c3,
            c1*c2*c3 + s1*s2*s3
        )
        EulerOrder.YZX -> Quat(
            s1*c2*c3 + c1*s2*s3,
            c1*s2*c3 + s1*c2*s3,
            c1*c2*s3 - s1*s2*c3,
            c1*c2*c3 - s1*s2*s3
        )
        EulerOrder.XZY -> Quat(
            s1*c2*c3 - c1*s2*s3,
            c1*s2*c3 - s1*c2*s3,
            c1*c2*s3 + s1*s2*c3,
            c1*c2*c3 + s1*s2*s3,
        )
    }
}

fun Quat.matrix(): Mat3 {
    // https://github.com/mrdoob/three.js/blob/dev/src/math/Matrix4.js # makeRotationFromQuaternion (#compose(zero, q, one))
    // note that we strip out the position and scale code (position = (0,0,0), scale = (1,1,1))
    // our matrices are in the opposite col/row order to three.js

    val x2 = x+x; val y2 = y+y; val z2 = z+z
    val xx = x*x2; val xy = x*y2; val xz = x*z2;
    val yy = y*y2; val yz = y*z2; val zz = z*z2;
    val wx = w*x2; val wy = w*y2; val wz = w*z2;

    return Mat3(
        1 - (yy+zz), xy-wz, xz+wy,
        xy+wz, 1 - (xx+zz), yz-wx,
        xz-wy, yz+wx, 1 - (xx+yy)
    )
}

fun Mat3.quat(): Quat {
    val trace = n00 + n11 + n22
    return when {
        trace >= 0 -> {
            val s = 0.5 / sqrt(trace + 1)
            Quat(
                (n21 - n12) * s,
                (n02 - n20) * s,
                (n10 - n01) * s,
                0.25 / s
            )
        }
        n00 > n11 && n00 > n22 -> {
            val s = 2.0 * sqrt(1.0 + n00 - n11 - n22)
            Quat(
                0.25 * s,
                (n01 + n10) / s,
                (n02 + n20) / s,
                (n21 - n12) / s
            )
        }
        n11 > n22 -> {
            val s = 2.0 * sqrt(1.0 + n11 - n00 - n22)
            Quat(
                (n01 + n10) / s,
                0.25 * s,
                (n12 + n21) / s,
                (n02 - n20) / s
            )
        }
        else -> {
            val s = 2.0 * sqrt(1.0 + n22 - n00 - n11)
            Quat(
                (n02 + n20) / s,
                (n12 + n21) / s,
                0.25 * s,
                (n10 - n01) / s
            )
        }
    }
}

fun Mat3.euler(order: EulerOrder): Vec3 {
    return when (order) {
        EulerOrder.XYZ -> {
            val y = asin(clamp(n02, -1.0, 1.0))
            if (abs(n02) < ONE_EPSILON) Vec3(
                atan2(-n12, n22),
                y,
                atan2(-n01, n00),
            ) else Vec3(
                atan2(n21, n11),
                y,
                0.0,
            )
        }
        EulerOrder.YXZ -> {
            val x = asin(-clamp(n12, -1.0, 1.0))
            if (abs(n12) < ONE_EPSILON) Vec3(
                x,
                atan2(n02, n22),
                atan2(n10, n11)
            ) else Vec3(
                x,
                atan2(-n20, n00),
                0.0
            )
        }
        EulerOrder.ZXY -> {
            val x = asin(clamp(n21, -1.0, 1.0))
            return if (abs(n21) < ONE_EPSILON) Vec3(
                x,
                atan2(-n20, n22),
                atan2(-n01, n11)
            ) else Vec3(
                x,
                0.0,
                atan2(n10, n00)
            )
        }
        EulerOrder.ZYX -> {
            val y = asin(-clamp(n20, -1.0, 1.0))
            if (abs(n20) < ONE_EPSILON) Vec3(
                atan2(n21, n22),
                y,
                atan2(n10, n00),
            ) else Vec3(
                0.0,
                y,
                atan2(-n01, n11),
            )
        }
        EulerOrder.YZX -> {
            val z = asin(clamp(n10, -1.0, 1.0))
            if (abs(n10) < ONE_EPSILON) Vec3(
                atan2(-n12, n11),
                atan2(-n20, n00),
                z,
            ) else Vec3(
                0.0,
                atan2(n02, n22),
                z,
            )
        }
        EulerOrder.XZY -> {
            val z = asin(-clamp(n01, -1.0, 1.0))
            return if (abs(n01) < ONE_EPSILON) Vec3(
                atan2(n21, n11),
                atan2(n02, n00),
                z
            ) else Vec3(
                atan2(-n12, n22),
                0.0,
                z
            )
        }
    }
}

fun Quat.euler(order: EulerOrder): Vec3 {
    return matrix().euler(order)
}
