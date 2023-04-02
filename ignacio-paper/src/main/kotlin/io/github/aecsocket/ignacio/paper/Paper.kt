package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.klam.DVec3
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.util.Vector

fun Location.position() = DVec3(x, y, z)
fun DVec3.location(world: World, yaw: Float = 0.0f, pitch: Float = 0.0f) = Location(world, x, y, z, yaw, pitch)

fun asKlam(v: Vector) = DVec3(v.x, v.y, v.z)

fun asPaper(v: DVec3) = Vector(v.x, v.y, v.z)

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
