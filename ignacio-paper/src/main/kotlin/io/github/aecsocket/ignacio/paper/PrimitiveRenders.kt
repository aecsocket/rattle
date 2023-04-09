package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.alexandria.Synchronized
import io.github.aecsocket.alexandria.paper.render.PaperRender
import io.github.aecsocket.alexandria.paper.render.RenderDescriptor
import io.github.aecsocket.alexandria.paper.render.playerTracker
import io.github.aecsocket.ignacio.Transform
import io.github.aecsocket.klam.FVec3
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class PrimitiveRenders internal constructor(private val ignacio: Ignacio) {
    inner class Instance(
        internal val id: Int,
        internal val render: PaperRender,
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
    private val state = Synchronized(State())

    val count get() = state.leak().instances.size

    fun create(world: World, transform: Transform, descriptor: RenderDescriptor): Int {
        val id = nextId.getAndIncrement()
        val location = transform.position.location(world)
        ignacio.scheduling.onChunk(location).launch {
            val marker = spawnMarkerEntity(location)
            val render = ignacio.renders.create(descriptor, marker.playerTracker(), transform)
            val instance = Instance(id, render, marker)

            state.synchronized { state ->
                state.instances[id] = instance
                state.entityToInstance[marker] = instance
            }

            ignacio.scheduling.onEntity(marker).launch {
                render.spawn()
            }
        }
        return id
    }

    fun destroy(id: Int): Boolean {
        return state.synchronized { state ->
            state.instances.remove(id)?.let { instance ->
                state.entityToInstance -= instance.marker
                instance.destroy()
                true
            } ?: false
        }
    }

    fun destroyAll() {
        state.synchronized { state ->
            state.instances.forEach { (_, instance) ->
                instance.destroy()
            }
            state.instances.clear()
            state.entityToInstance.clear()
        }
    }

    operator fun get(id: Int) = state.leak().instances[id]

    fun onEntityRemove(entity: Entity) {
        state.synchronized { state ->
            state.entityToInstance.remove(entity)?.let { instance ->
                state.instances -= instance.id
                instance.destroy()
            }
        }
    }

    internal fun onPlayerTrackEntity(player: Player, entity: Entity) {
        state.synchronized { state ->
            val instance = state.entityToInstance[entity] ?: return@synchronized
            instance.render.spawn(player)
        }
    }

    internal fun onPlayerUntrackEntity(player: Player, entity: Entity) {
        state.synchronized { state ->
            val instance = state.entityToInstance[entity] ?: return@synchronized
            instance.render.despawn(player)
        }
    }
}
