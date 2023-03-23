package io.github.aecsocket.ignacio.paper.util

import io.github.aecsocket.alexandria.core.math.Vec3d
import io.github.aecsocket.alexandria.core.math.Vec3f
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.util.Vector

fun Location.position() = Vec3d(x, y, z)
fun Vec3d.location(world: World, pitch: Float = 0f, yaw: Float = 0f) = Location(world, x, y, z, yaw, pitch)

fun Vector.toVec3d() = Vec3d(x, y, z)
fun Vector.toVec3f() = Vec3f(x.toFloat(), y.toFloat(), z.toFloat())
fun Vec3d.toVector() = Vector(x, y, z)
