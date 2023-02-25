package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.ignacio.core.BodyAccess
import io.github.aecsocket.ignacio.core.PhysicsSpace
import io.github.aecsocket.ignacio.core.bodies
import io.github.aecsocket.ignacio.core.math.Transform
import io.github.aecsocket.ignacio.paper.display.*
import io.github.aecsocket.ignacio.paper.util.location
import org.bukkit.World
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.entity.CreatureSpawnEvent

class PrimitiveBodies internal constructor(private val ignacio: Ignacio) {
    private data class Instance(
        val physics: PhysicsSpace,
        val body: BodyAccess,
        val render: WorldRender?,
    )

    private val bodies = HashMap<Entity, Instance>()

    fun create(
        world: World,
        transform: Transform,
        createBody: (physics: PhysicsSpace) -> BodyAccess,
        createRender: ((playerTracker: PlayerTracker) -> WorldRender)?,
    ) {
        val entity = world.spawnEntity(
            transform.position.location(world), EntityType.ARMOR_STAND, CreatureSpawnEvent.SpawnReason.COMMAND
        ) { entity ->
            entity as ArmorStand
            entity.isVisible = false
            entity.isMarker = true
            entity.isPersistent = false
            entity.setCanTick(false)
        }
        val physics = ignacio.physicsIn(world).physics
        val body = createBody(physics)
        physics.bodies.addBody(body)
        val render = createRender?.invoke(entity.playerTracker())
        bodies[entity] = Instance(physics, body, render)
    }

    internal fun update() {
        bodies.toMap().forEach { (entity, instance) ->
            if (!entity.isValid) {
                bodies.remove(entity)
                return@forEach
            }

            entity.teleport(instance.body.transform.position.location(entity.world))
            instance.render?.transform = instance.body.transform
        }
    }

    internal fun track(player: Player, entity: Entity) {
        val body = bodies[entity] ?: return
        body.render?.spawn(player)
    }

    internal fun untrack(player: Player, entity: Entity) {
        val body = bodies[entity] ?: return
        body.render?.despawn(player)
    }

    fun numBodies() = bodies.size

    fun removeAll() {
        bodies.forEach { (entity, instance) ->
            instance.physics.bodies.destroyBody(instance.body)
            instance.render?.despawn()
            entity.remove()
        }
        bodies.clear()
    }
}
