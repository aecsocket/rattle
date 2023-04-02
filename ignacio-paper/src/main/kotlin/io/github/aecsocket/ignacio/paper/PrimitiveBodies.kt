package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.alexandria.paper.extension.runDelayed
import io.github.aecsocket.ignacio.PhysicsBody
import io.github.aecsocket.ignacio.PhysicsSpace
import io.github.aecsocket.ignacio.Transform
import io.github.aecsocket.ignacio.paper.render.*
import io.github.aecsocket.ignacio.writeAs
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import java.util.concurrent.atomic.AtomicInteger

class PrimitiveBodies internal constructor(private val ignacio: Ignacio) {
    private data class Instance(
        val id: Int,
        val physics: PhysicsSpace,
        val body: PhysicsBody,
        val render: Render?,
        val marker: Entity,
    )

    private val nextId = AtomicInteger()
    private val lock = Any()
    private val instances = HashMap<Int, Instance>()
    private val entityToInstance = HashMap<Entity, Instance>()
    private var nextMove = emptyMap<Entity, Location>()

    val size get() = synchronized(lock) { instances.size }

    fun create(
        world: World,
        transform: Transform,
        createBody: (physics: PhysicsSpace) -> PhysicsBody,
        createRender: ((tracker: PlayerTracker) -> Render)?,
    ): Int {
        val id = nextId.getAndIncrement()
        spawnMarkerEntity(transform.position.location(world)) { marker ->
            val (physics) = ignacio.worlds.getOrCreate(world)
            val body = createBody(physics)
            physics.bodies.add(body)
            body.writeAs<PhysicsBody.MovingWrite> { moving ->
                moving.activate()
            }
            val render = createRender?.invoke(marker.playerTracker())
            val instance = Instance(id, physics, body, render, marker)
            synchronized(lock) {
                instances[id] = instance
                entityToInstance[marker] = instance
            }

            render?.let {
                ignacio.runDelayed {
                    it.spawn()
                }
            }
        }
        return id
    }

    private fun destroyInternal(instance: Instance) {
        instance.marker.remove()
        ignacio.engine.launchTask {
            instance.physics.bodies.remove(instance.body)
            instance.physics.bodies.destroy(instance.body)
        }
        instance.render?.despawn()
    }

    fun destroy(id: Int): Boolean {
        return synchronized(lock) {
            instances.remove(id)?.let { instance ->
                entityToInstance -= instance.marker
                destroyInternal(instance)
                true
            } ?: false
        }
    }

    fun destroyAll() {
        synchronized(lock) {
            instances.forEach { (_, instance) ->
                destroyInternal(instance)
            }
            instances.clear()
            entityToInstance.clear()
        }
    }

    operator fun get(id: Int) = instances[id]?.run { physics to body }

    internal fun syncUpdate() {
        // TODO Folia: tasks must be individually scheduled per entity
        nextMove.forEach { (entity, location) ->
            entity.teleport(location)
        }
    }

    internal fun physicsUpdate() {
        val nextMove = HashMap<Entity, Location>()
        synchronized(lock) {
            instances.toMap().forEach { (_, instance) ->
                if (!instance.marker.isValid || !instance.body.added) {
                    instances -= instance.id
                    entityToInstance -= instance.marker
                    destroyInternal(instance)
                    return@forEach
                }

                instance.body.read { body ->
                    val transform = body.transform
                    nextMove[instance.marker] = transform.position.location(instance.marker.world)
                    instance.render?.transform = transform
                }
            }
        }
        this.nextMove = nextMove
    }
}
