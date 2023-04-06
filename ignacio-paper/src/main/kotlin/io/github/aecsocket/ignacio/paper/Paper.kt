package io.github.aecsocket.ignacio.paper

import cloud.commandframework.ArgumentDescription
import cloud.commandframework.Command
import cloud.commandframework.types.tuples.Triplet
import io.github.aecsocket.alexandria.extension.typeToken
import io.github.aecsocket.klam.DVec3
import io.github.aecsocket.klam.FVec3
import io.github.aecsocket.klam.IVec3
import io.leangen.geantyref.TypeToken
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.util.Vector

fun Vector.asKlam() = DVec3(x, y, z)

fun DVec3.asPaper() = Vector(x, y, z)

fun Location.position() = DVec3(x, y, z)
fun DVec3.location(world: World, yaw: Float = 0.0f, pitch: Float = 0.0f) = Location(world, x, y, z, yaw, pitch)

fun Block.position() = IVec3(Math.floorDiv(x, 16), Math.floorDiv(y, 16), Math.floorDiv(z, 16))

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

fun <C> Command.Builder<C>.argumentFVec3(name: String) = argumentTriplet(name,
    typeToken<FVec3>(),
    Triplet.of("x", "y", "z"),
    Triplet.of(Float::class.java, Float::class.java, Float::class.java),
    { _, t -> FVec3(t.first, t.second, t.third) },
    ArgumentDescription.empty()
)

fun <C> Command.Builder<C>.argumentDVec3(name: String) = argumentTriplet(name,
    typeToken<DVec3>(),
    Triplet.of("x", "y", "z"),
    Triplet.of(Double::class.java, Double::class.java, Double::class.java),
    { _, t -> DVec3(t.first, t.second, t.third) },
    ArgumentDescription.empty()
)
