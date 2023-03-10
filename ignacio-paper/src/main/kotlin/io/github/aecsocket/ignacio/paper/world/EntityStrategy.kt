package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.ignacio.core.PhysicsBody
import io.github.aecsocket.ignacio.core.Destroyable
import io.github.aecsocket.ignacio.core.IgnacioEngine
import io.github.aecsocket.ignacio.core.PhysicsSpace
import org.bukkit.World
import org.bukkit.entity.Entity

interface EntityStrategy : PhysicsWorldHook, Destroyable {
    fun bodyOf(entity: Entity): PhysicsBody?

    fun entityOf(body: PhysicsBody): Entity?

    fun createFor(entity: Entity)

    fun destroyFor(entity: Entity)
}

fun interface EntityStrategyFactory {
    fun create(engine: IgnacioEngine, world: World, physics: PhysicsSpace): EntityStrategy
}

class NoOpEntityStrategy : EntityStrategy {
    override fun destroy() {}

    override fun enable() {}

    override fun disable() {}

    override fun tickUpdate() {}

    override fun physicsUpdate(deltaTime: Float) {}

    override fun bodyOf(entity: Entity) = null

    override fun entityOf(body: PhysicsBody) = null

    override fun createFor(entity: Entity) {}

    override fun destroyFor(entity: Entity) {}
}

class DefaultEntityStrategy(
    private val engine: IgnacioEngine,
    private val physics: PhysicsSpace,
) : EntityStrategy {
    private val entityToBody = HashMap<Entity, PhysicsBody>()
    private val bodyToEntity = HashMap<PhysicsBody, Entity>()

    override fun destroy() {

    }

    override fun enable() {}

    override fun disable() {}

    override fun tickUpdate() {}

    override fun physicsUpdate(deltaTime: Float) {}

    override fun bodyOf(entity: Entity) = entityToBody[entity]

    override fun entityOf(body: PhysicsBody) = bodyToEntity[body]

    override fun createFor(entity: Entity) {

    }

    override fun destroyFor(entity: Entity) {

    }
}
