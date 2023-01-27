package io.gitlab.aecsocket.ignacio.paper

import io.gitlab.aecsocket.ignacio.core.math.Vec3
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin

fun Vec3.location(world: World, yaw: Float = 0f, pitch: Float = 0f) = Location(world, x, y, z, yaw, pitch)
fun Location.vec3() = Vec3(x, y, z)

fun Plugin.registerEvents(listener: Listener) {
    Bukkit.getPluginManager().registerEvents(listener, this)
}

fun Plugin.runRepeating(period: Long = 1, delay: Long = 0, block: () -> Unit) {
    Bukkit.getScheduler().scheduleSyncRepeatingTask(this, block, delay, period)
}

fun Plugin.runDelayed(delay: Long = 0, block: () -> Unit) {
    Bukkit.getScheduler().scheduleSyncDelayedTask(this, block, delay)
}

fun Plugin.runAsync(block: suspend CoroutineScope.() -> Unit) {
    Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
        runBlocking { block() }
    })
}
