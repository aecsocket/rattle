package io.github.aecsocket.ignacio.paper

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import io.github.aecsocket.ignacio.paper.world.BlockUpdate
import io.papermc.paper.event.player.PlayerTrackEntityEvent
import io.papermc.paper.event.player.PlayerUntrackEntityEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockEvent
import org.bukkit.event.block.BlockPlaceEvent
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
        val entity = event.entity
        ignacio.primitiveBodies.onEntityRemove(entity)
        ignacio.primitiveRenders.onEntityRemove(entity)

        val world = ignacio.worlds[entity.world] ?: return
        world.entities.onEntityRemove(entity)
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

    private fun onBlock(event: BlockEvent, update: BlockUpdate) {
        val block = event.block
        val world = ignacio.worlds[block.world] ?: return
        world.terrain.onBlockUpdate(update)
    }

    @EventHandler
    fun on(event: BlockBreakEvent) { onBlock(event, BlockUpdate.Remove(event.block.position())) }

    @EventHandler
    fun on(event: BlockPlaceEvent) { onBlock(event, BlockUpdate.Set(event.block)) }

    @EventHandler
    fun on(event: EntityAddToWorldEvent) {
        val entity = event.entity
        val world = ignacio.worlds[entity.world] ?: return
        world.entities.onEntityAdd(entity)
    }
}
