package io.github.aecsocket.ignacio.core.math

typealias KRandom = kotlin.random.Random
typealias JRandom = java.util.Random

fun KRandom.nextVec3f() = Vec3f(nextFloat(), nextFloat(), nextFloat())
fun JRandom.nextVec3f() = Vec3f(nextFloat(), nextFloat(), nextFloat())

fun KRandom.nextVec3d() = Vec3d(nextDouble(), nextDouble(), nextDouble())
fun JRandom.nextVec3d() = Vec3d(nextDouble(), nextDouble(), nextDouble())
