package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.core.math.Quat
import io.github.aecsocket.ignacio.core.math.Vec3d
import io.github.aecsocket.ignacio.core.math.Vec3f
import jolt.math.JtQuat
import jolt.math.JtVec3d
import jolt.math.JtVec3f

fun JtVec3f.ignacio() = Vec3f(x, y, z)
fun JtVec3f.ignacioDp() = Vec3d(x.toDouble(), y.toDouble(), z.toDouble())
fun Vec3f.jolt() = JtVec3f(x, y, z)
fun Vec3f.joltDp() = JtVec3d(x.toDouble(), y.toDouble(), z.toDouble())

fun JtVec3d.ignacio() = Vec3d(x, y, z)
fun JtVec3d.ignacioSp() = Vec3f(x.toFloat(), y.toFloat(), z.toFloat())
fun Vec3d.jolt() = JtVec3d(x, y, z)
fun Vec3d.joltSp() = JtVec3f(x.toFloat(), y.toFloat(), z.toFloat())

fun JtQuat.ignacio() = Quat(x, y, z, w)
fun Quat.jolt() = JtQuat(x, y, z, w)
