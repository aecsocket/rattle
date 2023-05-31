package io.github.aecsocket.rattle.paper

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.WorldUnloadEvent

internal class RattleListener(private val rattle: PaperRattle) : Listener {
    @EventHandler
    fun on(event: WorldUnloadEvent) {
        rattle.destroyWorld(event.world)
    }

    @EventHandler
    fun on(event: PlayerQuitEvent) {
        rattle.destroyPlayerData(event.player)
    }
}
