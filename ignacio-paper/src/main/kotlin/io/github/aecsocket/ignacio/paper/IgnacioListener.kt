package io.github.aecsocket.ignacio.paper

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import io.papermc.paper.event.player.PlayerTrackEntityEvent
import io.papermc.paper.event.player.PlayerUntrackEntityEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerLocaleChangeEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.WorldUnloadEvent

internal class IgnacioListener(private val ignacio: Ignacio) : Listener {
    @EventHandler
    fun on(event: WorldUnloadEvent) {
        ignacio.worlds.destroy(event.world)
        ignacio.primitiveBodies.onWorldUnload(event.world)
    }

    @EventHandler
    fun on(event: PlayerQuitEvent) {
        ignacio.removePlayer(event.player)
    }

    @EventHandler
    fun on(event: PlayerLocaleChangeEvent) {
        ignacio.playerData(event.player).updateMessages()
    }

    @EventHandler
    fun on(event: EntityRemoveFromWorldEvent) {
        ignacio.primitiveBodies.onEntityRemove(event.entity)
        ignacio.primitiveRenders.onEntityRemove(event.entity)
    }

    @EventHandler
    fun on(event: PlayerTrackEntityEvent) {
        ignacio.primitiveBodies.onPlayerTrackEntity(event.player, event.entity)
        ignacio.primitiveRenders.onPlayerTrackEntity(event.player, event.entity)
    }

    @EventHandler
    fun on(event: PlayerUntrackEntityEvent) {
        ignacio.primitiveBodies.onPlayerUntrackEntity(event.player, event.entity)
        ignacio.primitiveRenders.onPlayerUntrackEntity(event.player, event.entity)
    }
}
