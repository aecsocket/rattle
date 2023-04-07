package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.alexandria.Synchronized
import io.github.aecsocket.ignacio.*
import io.github.aecsocket.ignacio.paper.Ignacio
import io.github.aecsocket.klam.*
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.HashMap

class DefaultEntityStrategy(
    private val ignacio: Ignacio,
    private val physics: PhysicsSpace,
) : EntityStrategy {
    private val engine = ignacio.engine
    private val destroyed = DestroyFlag()
    private val stepListener = StepListener { onPhysicsStep() }
    private val contactFilter = engine.contactFilter(engine.layers.entity)

    private inner class EntityData(
        val entity: Entity,
        val body: PhysicsBody,
        var transform: Transform,
    ) {
        val destroyed = AtomicBoolean(false)

        fun destroy() {
            if (destroyed.getAndSet(true)) return
            ignacio.engine.launchTask {
                physics.bodies.remove(body)
                physics.bodies.destroy(body)
            }
        }
    }

    private data class State(
        val entityToBody: MutableMap<Entity, EntityData> = HashMap(),
        val bodyToEntity: MutableMap<PhysicsBody, EntityData> = HashMap(),
    )

    private val state = Synchronized(State())
    private val entityShapes = Synchronized(EnumMap<EntityType, Shape?>(EntityType::class.java))

    var enabled = true
        private set

    init {
        physics.onStep(stepListener)
    }

    override fun destroy() {
        destroyed.mark()
        physics.removeStepListener(stepListener)
    }

    override fun enable() {
        enabled = true
    }

    override fun disable() {
        enabled = false
    }

    override fun entityOf(body: PhysicsBody): Entity? {
        return state.synchronized { state ->
            state.bodyToEntity[body]?.entity
        }
    }

    private fun onPhysicsStep() {
        if (!enabled) return

    }

    override fun onPhysicsUpdate(deltaTime: Float) {
        if (!enabled) return

        val entityMap = state.synchronized { it.entityToBody.toMap() }
        entityMap.forEach { (_, data) ->
            data.body.writeAs<PhysicsBody.MovingWrite> { body ->
                body.moveTo(data.transform, deltaTime)
            }
        }
    }

    override fun onEntityAdd(entity: Entity) {
        if (!enabled) return

        val shape = entityShape(entity) ?: return
        val transform = bodyTransform(entity)
        engine.launchTask {
            val bodyKey = physics.bodies.create(MovingBodyDescriptor(
                shape = shape,
                contactFilter = contactFilter,
                isKinematic = true,
                mass = Mass.Constant(60.0f), // TODO
                canDeactivate = false,
            ), transform)
            physics.bodies.add(bodyKey)
            bodyKey.writeAs<PhysicsBody.MovingWrite> { body ->
                body.activate()
            }
            val entityData = EntityData(entity, bodyKey, transform)
            state.synchronized { state ->
                state.entityToBody[entity] = entityData
                state.bodyToEntity[bodyKey] = entityData
            }

            ignacio.scheduling.onEntity(entity).runRepeating { task ->
                if (entityData.destroyed.get()) {
                    task.cancel()
                    return@runRepeating
                }
                entityData.transform = bodyTransform(entity)
            }
        }
    }

    private fun entityShape(entity: Entity): Shape? {
        return entityShapes.synchronized { entityShapes ->
            entityShapes.computeIfAbsent(entity.type) {
                when (entity) {
                    // player: 0.6 x 1.8 x 0.6
                    is Player -> engine.shape(CapsuleGeometry(0.9f, 0.3f))
                    else -> {
                        val halfWidth = (entity.width / 2).toFloat()
                        val halfHeight = (entity.height / 2).toFloat()
                        val convexRadius = DEFAULT_CONVEX_RADIUS
                        if (halfWidth <= convexRadius || halfHeight <= convexRadius) {
                            // entity's bounding box is too small for us to make a body for it
                            // marker armor stand or similar?
                            null
                        } else {
                            engine.shape(BoxGeometry(FVec3(halfWidth, halfHeight, halfWidth)))
                        }
                    }
                }
            }
        }
    }

    private fun bodyTransform(entity: Entity): Transform {
        val location = entity.location
        return Transform(
            DVec3(location.x, location.y + entity.height / 2, location.z),
            asQuat(FVec3(0.0f, radians(location.yaw), 0.0f), EulerOrder.XYZ),
        )
    }

    override fun onEntityRemove(entity: Entity) {
        if (!enabled) return

        val body = state.synchronized { state ->
            state.entityToBody.remove(entity)
        }?.body ?: return
        engine.launchTask {
            physics.bodies.remove(body)
            physics.bodies.destroy(body)
        }
    }
}
