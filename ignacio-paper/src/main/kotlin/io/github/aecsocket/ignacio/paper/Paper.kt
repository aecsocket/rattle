package io.github.aecsocket.ignacio.paper

import org.bukkit.Location
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.event.entity.CreatureSpawnEvent

fun spawnMarkerEntity(location: Location, block: (Entity) -> Unit = {}): Entity {
    return location.world.spawnEntity(location, EntityType.ARMOR_STAND, CreatureSpawnEvent.SpawnReason.COMMAND) { entity ->
        entity as ArmorStand
        entity.isVisible = false
        entity.isMarker = true
        entity.isPersistent = false
        entity.setCanTick(false)
        block(entity)
    }
}
