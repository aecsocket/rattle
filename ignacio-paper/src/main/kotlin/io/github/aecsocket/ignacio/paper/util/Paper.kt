package io.github.aecsocket.ignacio.paper.util

import org.bukkit.Bukkit
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin

fun Plugin.registerEvents(listener: Listener) =
    Bukkit.getPluginManager().registerEvents(listener, this)

fun Plugin.runRepeating(period: Long = 1, delay: Long = 0, block: () -> Unit) =
    Bukkit.getScheduler().scheduleSyncRepeatingTask(this, block, delay, period)

fun Plugin.runDelayed(delay: Long = 0, block: () -> Unit) =
    Bukkit.getScheduler().scheduleSyncDelayedTask(this, block, delay)
