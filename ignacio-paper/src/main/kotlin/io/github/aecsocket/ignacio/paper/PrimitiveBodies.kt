package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.alexandria.Synchronized
import io.github.aecsocket.alexandria.paper.extension.location
import io.github.aecsocket.alexandria.paper.render.*
import io.github.aecsocket.ignacio.*
import io.github.aecsocket.klam.*
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class PrimitiveBodies internal constructor(private val ignacio: Ignacio) {
    inner class Instance(
        internal val id: Int,
        internal val physics: PhysicsSpace,
        internal val body: PhysicsBody,
        internal val render: PaperRender?,
        internal val marker: Entity,
        internal var location: Location,
    ) {
        val destroyed = AtomicBoolean()

        internal var lastLocation = location

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

    private data class State(
        val instances: MutableMap<Int, Instance> = HashMap(),
        val entityToInstance: MutableMap<Entity, Instance> = HashMap(),
        val bodyToInstance: MutableMap<World, MutableMap<PhysicsBody, Instance>> = HashMap(),
    )

    private val nextId = AtomicInteger(1)
    private val state = Synchronized(State())
    private var teleportThresholdSq = 0.0

    // safety: it's just reading a field on an immutable ref to an object
    val count get() = state.leak().instances.size

    internal fun load() {
        teleportThresholdSq = sqr(ignacio.settings.primitiveBodies.teleportThreshold)
    }

    fun create(
        world: World,
        position: DVec3,
        createBody: (physics: PhysicsSpace) -> PhysicsBody,
        createRender: ((tracker: PlayerTracker) -> PaperRender)?,
    ): Int {
        val id = nextId.getAndIncrement()
        val location = position.location(world)
        ignacio.scheduling.onChunk(location).launch {
            val marker = spawnMarkerEntity(location)
            val (physics) = ignacio.worlds.getOrCreate(world)
            ignacio.engine.launchTask {
                val body = createBody(physics)
                physics.bodies.add(body)
                body.writeAs<PhysicsBody.MovingWrite> { moving ->
                    moving.activate()
                    physics.constraints.add(
                        descriptor = FixedJointDescriptor(
                            pointB = DVec3(0.0, 128.0, 0.0),
                        ),
                        targetA = moving,
                        targetB = ConstraintTarget.World,
                    )
                }
                val render = createRender?.invoke(marker.playerTracker())
                val instance = Instance(id, physics, body, render, marker, location)

                state.synchronized { state ->
                    state.instances[id] = instance
                    state.entityToInstance[marker] = instance
                    state.bodyToInstance.computeIfAbsent(world) { HashMap() }[body] = instance
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
                    // only teleport after a threshold, since on Folia teleporting is somewhat expensive
                    if (instance.lastLocation.distanceSquared(instance.location) >= teleportThresholdSq) {
                        instance.lastLocation = instance.location
                        marker.teleportAsync(instance.location)
                    }
                }
            }
        }
        return id
    }

    private fun removeMapping(state: State, instance: Instance) {
        state.instances -= instance.id
        state.entityToInstance -= instance.marker
        state.bodyToInstance[instance.marker.world]?.remove(instance.body)
    }

    fun destroy(id: Int): Boolean {
        return state.synchronized { state ->
            state.instances.remove(id)?.let { instance ->
                removeMapping(state, instance)
                instance.destroyed
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
            state.bodyToInstance.clear()
        }
    }

    operator fun get(id: Int) = state.leak().instances[id]

    internal fun onPhysicsUpdate() {
        val toRemove = HashSet<Instance>()
        state.synchronized { it.instances.toMap() }.forEach { (_, instance) ->
            if (!instance.marker.isValid || !instance.body.isAdded) {
                toRemove += instance
                return@forEach
            }

            instance.body.read { body ->
                if (!body.isActive) return@read
                val position = body.position
                val rotation = body.rotation
                instance.location = position.location(instance.marker.world)
                instance.render?.let { render ->
                    render.basePosition = position
                    render.transform = FAffine3(
                        FVec3(0.0f),
                        rotation,
                        render.transform.scale,
                    )
                }
            }
        }

        if (toRemove.isNotEmpty()) {
            state.synchronized { state ->
                toRemove.forEach { instance ->
                    removeMapping(state, instance)
                    instance.destroy()
                }
            }
        }
    }

    internal fun onWorldUnload(world: World) {
        // keep the lock held because of `removeMapping`
        state.synchronized { state ->
            state.bodyToInstance.remove(world)?.forEach { (_, instance) ->
                removeMapping(state, instance)
                instance.destroy()
            }
        }
    }

    internal fun onEntityRemove(entity: Entity) {
        // keep the lock held because of `removeMapping`
        state.synchronized { state ->
            state.entityToInstance.remove(entity)?.let { instance ->
                removeMapping(state, instance)
                instance.destroy()
            }
        }
    }

    internal fun onPlayerTrackEntity(player: Player, entity: Entity) {
        val instance = state.synchronized { it.entityToInstance[entity] } ?: return
        instance.render?.spawn(player)
    }

    internal fun onPlayerUntrackEntity(player: Player, entity: Entity) {
        val instance = state.synchronized { it.entityToInstance[entity] } ?: return
        instance.render?.despawn(player)
    }

    internal fun onPhysicsDestroy(world: World) {
        state.synchronized { state ->
            state.bodyToInstance.remove(world)?.forEach { (_, instance) ->
                removeMapping(state, instance)
                instance.destroy()
            }
        }
    }
}
