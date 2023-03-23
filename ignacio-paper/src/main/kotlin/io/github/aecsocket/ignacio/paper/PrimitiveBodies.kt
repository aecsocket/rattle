package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.ignacio.core.PhysicsBody
import io.github.aecsocket.ignacio.core.PhysicsSpace
import io.github.aecsocket.ignacio.core.bodies
import io.github.aecsocket.alexandria.core.math.Transform
import io.github.aecsocket.ignacio.paper.display.*
import io.github.aecsocket.ignacio.paper.util.location
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.entity.CreatureSpawnEvent

class PrimitiveBodies internal constructor(private val ignacio: Ignacio) {
    private data class Instance(
        val physics: PhysicsSpace,
        val body: PhysicsBody,
        val render: WorldRender?,
    )

    private val bodies = HashMap<Entity, Instance>()
    private var nextMove: Map<Entity, Location> = emptyMap()

    fun create(
        world: World,
        transform: Transform,
        addBody: (physics: PhysicsSpace, name: String) -> PhysicsBody,
        createRender: ((playerTracker: PlayerTracker) -> WorldRender)?,
    ) {
        world.spawnEntity(
            transform.position.location(world), EntityType.ARMOR_STAND, CreatureSpawnEvent.SpawnReason.COMMAND
        ) { entity ->
            entity as ArmorStand
            entity.isVisible = false
            entity.isMarker = true
            entity.isPersistent = false
            entity.setCanTick(false)

            val (physics) = ignacio.worlds.getOrCreate(world)
            val body = addBody(physics, ignacioBodyName("Primitive-${bodies.size}"))
            val render = createRender?.invoke(entity.playerTracker())
            bodies[entity] = Instance(physics, body, render)
        }
    }

    internal fun tickUpdate() {
        nextMove.forEach { (entity, location) ->
            entity.teleport(location)
        }
    }

    internal fun physicsUpdate() {
        // double buffer nextMove
        val nextMove = HashMap<Entity, Location>()
        bodies.toMap().forEach { (entity, instance) ->
            fun destroy() {
                entity.remove()
                instance.physics.bodies {
                    remove(instance.body)
                    destroy(instance.body)
                }
                instance.render?.despawn()
                bodies.remove(entity)
            }

            if (!entity.isValid || !instance.body.valid) {
                destroy()
                return@forEach
            }

            instance.body.read { body ->
                val transform = body.transform
                nextMove[entity] = transform.position.location(entity.world)
                instance.render?.transform = transform
            }
        }
        this.nextMove = nextMove
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
            instance.physics.bodies {
                remove(instance.body)
                destroy(instance.body)
            }
            instance.render?.despawn()
            entity.remove()
        }
        bodies.clear()
    }
}
