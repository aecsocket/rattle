package io.github.aecsocket.rattle.paper

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.WorldUnloadEvent

internal class RattleListener(private val rattle: RattlePlugin) : Listener {
    @EventHandler
    fun on(event: PlayerQuitEvent) {
        rattle.removePlayerData(event.player)
    }

    @EventHandler
    fun on(event: WorldUnloadEvent) {
        rattle.removeWorld(event.world)
    }
}
