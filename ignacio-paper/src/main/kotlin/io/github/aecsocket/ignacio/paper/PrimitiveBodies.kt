package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.ignacio.*
import io.github.aecsocket.ignacio.paper.render.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class PrimitiveBodies internal constructor(private val ignacio: Ignacio) {
    private inner class Instance(
        val id: Int,
        val physics: PhysicsSpace,
        val body: PhysicsBody,
        val render: Render?,
        val marker: Entity,
        var location: Location,
    ) {
        val destroyed = AtomicBoolean()

        fun destroy() {
            if (destroyed.getAndSet(true)) return
            render?.despawn()
            ignacio.scheduling.onEntity(marker).launch {
                marker.remove()
            }
            ignacio.engine.launchTask {
                physics.bodies.remove(body)
                physics.bodies.destroy(body)
            }
        }
    }

    private val nextId = AtomicInteger(1)
    private val mutex = Mutex()
    private val instances = HashMap<Int, Instance>()
    private val entityToInstance = HashMap<Entity, Instance>()
    private val bodyToInstance = HashMap<World, MutableMap<PhysicsBody, Instance>>()

    val count get() = instances.size

    fun create(
        world: World,
        transform: Transform,
        createBody: (physics: PhysicsSpace) -> PhysicsBody,
        createRender: ((tracker: PlayerTracker) -> Render)?,
    ): Int {
        val id = nextId.getAndIncrement()
        val location = transform.position.location(world)
        ignacio.scheduling.onChunk(location).launch {
            val marker = spawnMarkerEntity(location)
            val (physics) = ignacio.worlds.getOrCreate(world)
            ignacio.engine.launchTask {
                val body = createBody(physics)
                physics.bodies.add(body)
                body.writeAs<PhysicsBody.MovingWrite> { moving ->
                    moving.activate()
                }
                val render = createRender?.invoke(marker.playerTracker())
                val instance = Instance(id, physics, body, render, marker, location)

                mutex.withLock {
                    instances[id] = instance
                    entityToInstance[marker] = instance
                    bodyToInstance.computeIfAbsent(world) { HashMap() }[body] = instance
                }

                render?.let {
                    ignacio.scheduling.onEntity(marker).launch {
                        it.spawn()
                    }
                }
                ignacio.scheduling.onEntity(marker).runRepeating { task ->
                    if (instance.destroyed.get()) {
                        task.cancel()
                        return@runRepeating
                    }
                    marker.teleport(instance.location)
                }
            }
        }
        return id
    }

    private fun removeMapping(instance: Instance) {
        instances -= instance.id
        entityToInstance -= instance.marker
        bodyToInstance[instance.marker.world]?.remove(instance.body)
    }

    suspend fun destroy(id: Int): Boolean {
        return mutex.withLock {
            instances.remove(id)?.let { instance ->
                removeMapping(instance)
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
            bodyToInstance.clear()
        }
    }

    operator fun get(id: Int) = instances[id]?.run { physics to body }

    internal suspend fun onPhysicsUpdate() {
        val toRemove = HashSet<Instance>()
        mutex.withLock { instances.toMap() }.forEach { (_, instance) ->
            if (!instance.marker.isValid || !instance.body.added) {
                toRemove += instance
                return@forEach
            }

            instance.body.read { body ->
                val transform = body.transform
                instance.location = transform.position.location(instance.marker.world)
                instance.render?.transform = transform
            }
        }

        if (toRemove.isNotEmpty()) {
            mutex.withLock {
                toRemove.forEach { instance ->
                    removeMapping(instance)
                    instance.destroy()
                }
            }
        }
    }

    internal suspend fun onWorldUnload(world: World) {
        // keep the lock held because of `removeMapping`
        mutex.withLock {
            bodyToInstance.remove(world)?.forEach { (_, instance) ->
                removeMapping(instance)
                instance.destroy()
            }
        }
    }

    internal suspend fun onEntityRemove(entity: Entity) {
        // keep the lock held because of `removeMapping`
        mutex.withLock {
            entityToInstance.remove(entity)?.let { instance ->
                removeMapping(instance)
                instance.destroy()
            }
        }
    }

    internal suspend fun onPlayerTrackEntity(player: Player, entity: Entity) {
        val instance = mutex.withLock { entityToInstance[entity] } ?: return
        instance.render?.spawn(player)
    }

    internal suspend fun onPlayerUntrackEntity(player: Player, entity: Entity) {
        val instance = mutex.withLock { entityToInstance[entity] } ?: return
        instance.render?.despawn(player)
    }
}
