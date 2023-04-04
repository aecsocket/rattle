package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.ignacio.Transform
import io.github.aecsocket.ignacio.paper.render.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class PrimitiveRenders internal constructor(private val ignacio: Ignacio) {
    private inner class Instance(
        val id: Int,
        val render: Render,
        val marker: Entity,
    ) {
        val destroyed = AtomicBoolean()

        fun destroy() {
            if (destroyed.getAndSet(true)) return
            render.despawn()
        }
    }

    private val nextId = AtomicInteger(1)
    private val mutex = Mutex()
    private val instances = HashMap<Int, Instance>()
    private val entityToInstance = HashMap<Entity, Instance>()

    val count get() = instances.size

    fun create(world: World, transform: Transform, descriptor: RenderDescriptor): Int {
        val id = nextId.getAndIncrement()
        val location = transform.position.location(world)
        ignacio.scheduling.onChunk(location).launch {
            val marker = spawnMarkerEntity(location)
            val render = ignacio.renders.create(descriptor, marker.playerTracker(), transform)
            val instance = Instance(id, render, marker)

            mutex.withLock {
                instances[id] = instance
                entityToInstance[marker] = instance
            }

            ignacio.scheduling.onEntity(marker).launch {
                render.spawn()
            }
            ignacio.scheduling.onEntity(marker).runRepeating { task ->
                if (instance.destroyed.get()) {
                    task.cancel()
                    return@runRepeating
                }
                marker.teleportAsync(instance.render.transform.position.location(marker.world))
            }
        }
        return id
    }

    suspend fun destroy(id: Int): Boolean {
        return mutex.withLock {
            instances.remove(id)?.let { instance ->
                entityToInstance -= instance.marker
                instance.destroy()
                true
            } ?: false
        }
    }

    suspend fun destroyAll() {
        mutex.withLock {
            instances.forEach { (_, instance) ->
                instance.destroy()
            }
            instances.clear()
            entityToInstance.clear()

        }
    }

    operator fun get(id: Int) = instances[id]?.render

    internal suspend fun onEntityRemove(entity: Entity) {
        mutex.withLock {
            entityToInstance.remove(entity)?.let { instance ->
                instances -= instance.id
                instance.destroy()
            }
        }
    }

    internal suspend fun onPlayerTrackEntity(player: Player, entity: Entity) {
        mutex.withLock {
            val instance = entityToInstance[entity] ?: return
            instance.render.spawn(player)
        }
    }

    internal suspend fun onPlayerUntrackEntity(player: Player, entity: Entity) {
        mutex.withLock {
            val instance = entityToInstance[entity] ?: return
            instance.render.despawn(player)
        }
    }
}
