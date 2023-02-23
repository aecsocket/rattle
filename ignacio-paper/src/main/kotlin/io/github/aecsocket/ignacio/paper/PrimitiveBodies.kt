package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.ignacio.core.PhysicsBody
import io.github.aecsocket.ignacio.core.PhysicsSpace
import io.github.aecsocket.ignacio.core.math.Transform
import io.github.aecsocket.ignacio.paper.display.*
import io.github.aecsocket.ignacio.paper.util.location
import org.bukkit.World
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.event.entity.CreatureSpawnEvent

class PrimitiveBodies internal constructor(private val ignacio: Ignacio) {
    private data class Instance(
        val physics: PhysicsSpace,
        val body: PhysicsBody,
        val entity: Entity,
        val render: WorldRender?,
    )

    private val bodies = ArrayList<Instance>()

    fun create(
        world: World,
        transform: Transform,
        createBody: (physics: PhysicsSpace) -> PhysicsBody,
        createRender: ((playerTracker: PlayerTracker) -> WorldRender)?,
    ) {
        val physics = ignacio.physicsIn(world)
        val entity = world.spawnEntity(
            transform.position.location(world), EntityType.ARMOR_STAND, CreatureSpawnEvent.SpawnReason.COMMAND
        ) { entity ->
            entity as ArmorStand
            entity.isVisible = false
            entity.isMarker = true
            entity.isPersistent = false
            entity.setCanTick(false)
        }
        val body = createBody(physics)
        val render = createRender?.invoke(entity.playerTracker())
            ?.also { it.spawn() }
        bodies += Instance(physics, body, entity, render)
    }

    internal fun update() {
        bodies.forEach { instance ->
            instance.entity.teleport(instance.body.transform.position.location(instance.entity.world))
            instance.render?.transform = instance.body.transform
        }
    }

    fun numBodies() = bodies.size

    fun removeAll() {
        bodies.forEach { instance ->
            instance.physics.removeBody(instance.body)
            instance.body.destroy()
            instance.render?.despawn()
            instance.entity.remove()
        }
        bodies.clear()
    }
}
