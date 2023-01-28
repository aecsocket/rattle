package io.gitlab.aecsocket.ignacio.paper

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import io.gitlab.aecsocket.ignacio.core.*
import io.gitlab.aecsocket.ignacio.core.math.Transform
import io.papermc.paper.event.player.PlayerTrackEntityEvent
import io.papermc.paper.event.player.PlayerUntrackEntityEvent
import org.bukkit.Location
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityPortalEvent

internal data class DebugPrimitive(
    var physSpace: IgPhysicsSpace,
    val body: IgDynamicBody,
    val mesh: IgMesh?
)

internal class IgPrimitives(private val ignacio: Ignacio) : Listener {
    private val primitives = HashMap<Entity, DebugPrimitive>()

    fun enable() {
        ignacio.runRepeating {
            primitives.toMap().forEach { (entity, primitive) ->
                if (!primitive.body.isAdded()) {
                    primitives.remove(entity)
                    entity.remove()
                    remove(entity, primitive)
                    return@forEach
                }

                ignacio.executePhysics {
                    val transform = primitive.body.transform
                    primitive.mesh?.transform = transform
                    ignacio.runDelayed {
                        @Suppress("UnstableApiUsage")
                        entity.teleport(transform.position.location(entity.world), true)
                    }
                }
            }
        }
    }

    fun countPrimitives() = primitives.size

    fun create(location: Location, geometry: IgGeometry, mass: IgScalar, visual: Boolean) {
        val entity = location.world.spawnEntity(
            location,
            EntityType.ARMOR_STAND,
            CreatureSpawnEvent.SpawnReason.COMMAND
        ) { entity ->
            entity as ArmorStand
            entity.isVisible = false
            entity.isPersistent = false
            entity.setAI(false)
            entity.setGravity(false)
        }
        val transform = Transform(location.vec3())
        val mesh = if (visual) {
            val mesh = ignacio.meshes.createItem(
                transform,
                { entity.trackedPlayers },
                ignacio.settings.debugMeshSettings,
                ignacio.settings.debugMeshItem.createItem()
            )
            mesh.spawn()
            mesh
        } else null

        ignacio.executePhysics {
            val physSpace = ignacio.physicsSpaceOf(location.world)
            val body = ignacio.backend.createDynamicBody(
                transform,
                IgBodyDynamics(mass = mass)
            )
            body.setGeometry(geometry)
            physSpace.addBody(body)
            primitives[entity] = DebugPrimitive(physSpace, body, mesh)
        }
    }

    fun removeAll() {
        primitives.forEach { (entity, primitive) ->
            entity.remove()
            remove(entity, primitive)
        }
        primitives.clear()
    }

    private fun remove(entity: Entity, primitive: DebugPrimitive) {
        primitive.mesh?.let { ignacio.meshes.remove(it) }
        val physSpace = ignacio.physicsSpaceOfOrNull(entity.world) ?: return
        ignacio.executePhysics {
            physSpace.removeBody(primitive.body)
            primitive.body.destroy()
        }
    }

    @EventHandler
    fun on(event: EntityRemoveFromWorldEvent) {
        val entity = event.entity
        val primitive = primitives.remove(entity) ?: return
        remove(entity, primitive)
    }

    @EventHandler
    fun on(event: PlayerTrackEntityEvent) {
        val primitive = primitives[event.entity] ?: return
        primitive.mesh?.spawn(event.player)
    }

    @EventHandler
    fun on(event: PlayerUntrackEntityEvent) {
        val primitive = primitives[event.entity] ?: return
        primitive.mesh?.despawn(event.player)
    }

    @EventHandler
    fun on(event: EntityPortalEvent) {
        val primitive = primitives[event.entity] ?: return
        primitive.physSpace.removeBody(primitive.body)
        val newPhysSpace = ignacio.physicsSpaceOf(event.to!!.world)
        newPhysSpace.addBody(primitive.body)
    }
}
