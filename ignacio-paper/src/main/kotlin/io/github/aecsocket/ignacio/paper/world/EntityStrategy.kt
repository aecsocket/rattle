package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.ignacio.core.BodyAccess
import io.github.aecsocket.ignacio.core.Destroyable
import io.github.aecsocket.ignacio.core.PhysicsSpace
import io.github.aecsocket.ignacio.paper.Ignacio
import org.bukkit.entity.Entity

interface EntityStrategy : Destroyable {
    fun bodyOf(entity: Entity): BodyAccess?

    fun entityOf(body: BodyAccess): Entity?

    fun createFor(entity: Entity)

    fun destroyFor(entity: Entity)
}

class NoOpEntityStrategy : EntityStrategy {
    override fun destroy() {}

    override fun bodyOf(entity: Entity) = null

    override fun entityOf(body: BodyAccess) = null

    override fun createFor(entity: Entity) {}

    override fun destroyFor(entity: Entity) {}
}

class DefaultEntityStrategy(
    private val ignacio: Ignacio,
    private val physics: PhysicsSpace,
) : EntityStrategy {
    private val entityToBody = HashMap<Entity, BodyAccess>()
    private val bodyToEntity = HashMap<BodyAccess, Entity>()

    override fun destroy() {

    }

    override fun bodyOf(entity: Entity) = entityToBody[entity]

    override fun entityOf(body: BodyAccess) = bodyToEntity[body]

    override fun createFor(entity: Entity) {

    }

    override fun destroyFor(entity: Entity) {

    }
}
