package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.ignacio.core.BodyRef
import io.github.aecsocket.ignacio.core.Destroyable
import io.github.aecsocket.ignacio.core.PhysicsSpace
import io.github.aecsocket.ignacio.paper.Ignacio
import org.bukkit.entity.Entity

interface EntityStrategy : Destroyable {
    fun bodyOf(entity: Entity): BodyRef?

    fun entityOf(body: BodyRef): Entity?

    fun createFor(entity: Entity)

    fun destroyFor(entity: Entity)
}

class NoOpEntityStrategy : EntityStrategy {
    override fun destroy() {}

    override fun bodyOf(entity: Entity) = null

    override fun entityOf(body: BodyRef) = null

    override fun createFor(entity: Entity) {}

    override fun destroyFor(entity: Entity) {}
}

class DefaultEntityStrategy(
    private val ignacio: Ignacio,
    private val physics: PhysicsSpace,
) : EntityStrategy {
    private val entityToBody = HashMap<Entity, BodyRef>()
    private val bodyToEntity = HashMap<BodyRef, Entity>()

    override fun destroy() {

    }

    override fun bodyOf(entity: Entity) = entityToBody[entity]

    override fun entityOf(body: BodyRef) = bodyToEntity[body]

    override fun createFor(entity: Entity) {

    }

    override fun destroyFor(entity: Entity) {

    }
}
