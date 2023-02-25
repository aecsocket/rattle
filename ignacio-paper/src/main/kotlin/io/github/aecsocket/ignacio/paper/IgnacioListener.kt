package io.github.aecsocket.ignacio.paper

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerLocaleChangeEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent

internal class IgnacioListener(private val ignacio: Ignacio) : Listener {
    @EventHandler
    fun on(event: PlayerQuitEvent) {
        ignacio.removePlayerData(event.player)
    }

    @EventHandler
    fun on(event: PlayerLocaleChangeEvent) {
        ignacio.playerData(event.player).updateMessages(event.player.locale())
    }

    @EventHandler
    fun on(event: ChunkLoadEvent) {
        val world = ignacio.physicsInOr(event.world) ?: return
        world.load(event.chunk)
    }

    @EventHandler
    fun on(event: ChunkUnloadEvent) {
        val world = ignacio.physicsInOr(event.world) ?: return
        world.unload(event.chunk)
    }
}
