package io.github.aecsocket.ignacio.core.math

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

private const val FPI = PI.toFloat()
internal const val DECIMAL_FORMAT = "%.3f"

// scalars

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
