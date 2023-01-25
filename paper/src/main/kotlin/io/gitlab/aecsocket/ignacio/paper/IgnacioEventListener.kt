package io.gitlab.aecsocket.ignacio.paper

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.WorldLoadEvent
import org.bukkit.event.world.WorldUnloadEvent

internal class IgnacioEventListener(private val ignacio: Ignacio) : Listener {
    @EventHandler
    fun on(event: WorldLoadEvent) {
        ignacio.spaceOf(event.world)
    }

    @EventHandler
    fun on(event: WorldUnloadEvent) {
        ignacio.removeSpace(event.world)
    }
}
