package io.gitlab.aecsocket.ignacio.paper

import io.gitlab.aecsocket.ignacio.core.math.Vec3
import org.bukkit.Location
import org.bukkit.World

fun Vec3.location(world: World, yaw: Float = 0f, pitch: Float = 0f) = Location(world, x, y, z, yaw, pitch)
fun Location.vec3() = Vec3(x, y, z)
