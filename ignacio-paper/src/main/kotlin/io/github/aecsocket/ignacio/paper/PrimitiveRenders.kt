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

    private val nextRenderId = AtomicInteger()
    private val lock = Any()
    private val instances = HashMap<Int, Instance>()
    private val entityToRender = HashMap<Entity, Instance>()

    val size get() = synchronized(lock) { instances.size }

    fun create(world: World, transform: Transform, descriptor: RenderDescriptor): Int {
        val id = nextRenderId.incrementAndGet()
        spawnMarkerEntity(transform.position.location(world)) { marker ->
            val render = ignacio.renders.create(descriptor, marker.playerTracker(), transform)
            val instance = Instance(id, render, marker)
            synchronized(lock) {
                instances[id] = instance
                entityToRender[marker] = instance
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
                entityToRender -= instance.marker
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
            entityToRender.clear()
        }
    }

    operator fun get(id: Int) = instances[id]?.render

    internal fun syncUpdate() {
        // TODO Folia: tasks must be individually scheduled per entity
        synchronized(lock) {
            entityToRender.forEach { (entity, instance) ->
                entity.teleport(instance.render.transform.position.location(entity.world))
            }
        }
    }

    internal fun onEntityRemove(entity: Entity) {
        synchronized(lock) {
            entityToRender.remove(entity)?.let { instance ->
                instances -= instance.id
                destroyInternal(instance)
            }
        }
    }

    internal fun onPlayerTrackEntity(player: Player, entity: Entity) {
        synchronized(lock) {
            val instance = entityToRender[entity] ?: return
            instance.render.spawn(player)
        }
    }

    internal fun onPlayerUntrackEntity(player: Player, entity: Entity) {
        synchronized(lock) {
            val instance = entityToRender[entity] ?: return
            instance.render.despawn(player)
        }
    }
}
