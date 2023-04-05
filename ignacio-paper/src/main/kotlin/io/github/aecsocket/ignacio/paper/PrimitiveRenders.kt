package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.alexandria.Mutexed
import io.github.aecsocket.ignacio.Transform
import io.github.aecsocket.ignacio.paper.render.*
import io.github.aecsocket.klam.FVec3
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class PrimitiveRenders internal constructor(private val ignacio: Ignacio) {
    inner class Instance(
        internal val id: Int,
        internal val render: Render,
        internal val marker: Entity,
    ) {
        val destroyed = AtomicBoolean()

        var transform: Transform
            get() = render.transform
            set(value) {
                ignacio.scheduling.onEntity(marker).launch {
                    render.transform = value
                }
            }

        var scale: FVec3
            get() = render.scale
            set(value) {
                ignacio.scheduling.onEntity(marker).launch {
                    render.scale = value
                }
            }

        fun destroy() {
            if (destroyed.getAndSet(true)) return
            render.despawn()
        }
    }

    private data class State(
        val instances: MutableMap<Int, Instance> = HashMap(),
        val entityToInstance: MutableMap<Entity, Instance> = HashMap(),
    )

    private val nextId = AtomicInteger(1)
    private val state = Mutexed(State())

    val count get() = state.leak().instances.size

    fun create(world: World, transform: Transform, descriptor: RenderDescriptor): Int {
        val id = nextId.getAndIncrement()
        val location = transform.position.location(world)
        ignacio.scheduling.onChunk(location).launch {
            val marker = spawnMarkerEntity(location)
            val render = ignacio.renders.create(descriptor, marker.playerTracker(), transform)
            val instance = Instance(id, render, marker)

            state.withLock { state ->
                state.instances[id] = instance
                state.entityToInstance[marker] = instance
            }

            ignacio.scheduling.onEntity(marker).launch {
                render.spawn()
            }
        }
        return id
    }

    suspend fun destroy(id: Int): Boolean {
        return state.withLock { state ->
            state.instances.remove(id)?.let { instance ->
                state.entityToInstance -= instance.marker
                instance.destroy()
                true
            } ?: false
        }
    }

    suspend fun destroyAll() {
        state.withLock { state ->
            state.instances.forEach { (_, instance) ->
                instance.destroy()
            }
            state.instances.clear()
            state.entityToInstance.clear()
        }
    }

    operator fun get(id: Int) = state.leak().instances[id]

    internal suspend fun onEntityRemove(entity: Entity) {
        state.withLock { state ->
            state.entityToInstance.remove(entity)?.let { instance ->
                state.instances -= instance.id
                instance.destroy()
            }
        }
    }

    internal suspend fun onPlayerTrackEntity(player: Player, entity: Entity) {
        state.withLock { state ->
            val instance = state.entityToInstance[entity] ?: return@withLock
            instance.render.spawn(player)
        }
    }

    internal suspend fun onPlayerUntrackEntity(player: Player, entity: Entity) {
        state.withLock { state ->
            val instance = state.entityToInstance[entity] ?: return@withLock
            instance.render.despawn(player)
        }
    }
}
