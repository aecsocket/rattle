package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.alexandria.paper.extension.runDelayed
import io.github.aecsocket.ignacio.Transform
import io.github.aecsocket.ignacio.paper.render.*
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import java.util.concurrent.atomic.AtomicInteger

class PrimitiveRenders internal constructor(private val ignacio: Ignacio) {
    private data class Instance(
        val id: Int,
        val render: Render,
        val marker: Entity,
    )

    private val nextId = AtomicInteger()
    private val lock = Any()
    private val instances = HashMap<Int, Instance>()
    private val entityToInstance = HashMap<Entity, Instance>()

    val count get() = synchronized(lock) { instances.size }

    fun create(world: World, transform: Transform, descriptor: RenderDescriptor): Int {
        val id = nextId.getAndIncrement()
        spawnMarkerEntity(transform.position.location(world)) { marker ->
            val render = ignacio.renders.create(descriptor, marker.playerTracker(), transform)
            val instance = Instance(id, render, marker)
            synchronized(lock) {
                instances[id] = instance
                entityToInstance[marker] = instance
            }

            ignacio.runDelayed {
                render.spawn()
            }
        }
        return id
    }

    private fun destroyInternal(instance: Instance) {
        instance.render.despawn()
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

    operator fun get(id: Int) = instances[id]?.render

    internal fun syncUpdate() {
        // TODO Folia: tasks must be individually scheduled per entity
        synchronized(lock) {
            entityToInstance.forEach { (entity, instance) ->
                entity.teleport(instance.render.transform.position.location(entity.world))
            }
        }
    }

    internal fun onEntityRemove(entity: Entity) {
        synchronized(lock) {
            entityToInstance.remove(entity)?.let { instance ->
                instances -= instance.id
                destroyInternal(instance)
            }
        }
    }

    internal fun onPlayerTrackEntity(player: Player, entity: Entity) {
        synchronized(lock) {
            val instance = entityToInstance[entity] ?: return
            instance.render.spawn(player)
        }
    }

    internal fun onPlayerUntrackEntity(player: Player, entity: Entity) {
        synchronized(lock) {
            val instance = entityToInstance[entity] ?: return
            instance.render.despawn(player)
        }
    }
}
