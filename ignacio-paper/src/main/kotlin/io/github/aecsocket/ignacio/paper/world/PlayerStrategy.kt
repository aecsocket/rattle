package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.ignacio.core.Destroyable
import io.github.aecsocket.ignacio.core.IgnacioEngine
import io.github.aecsocket.ignacio.core.PhysicsSpace
import org.bukkit.World

interface PlayerStrategy : PhysicsWorldHook, Destroyable {

}

fun interface PlayerStrategyFactory {
    fun create(engine: IgnacioEngine, world: World, physics: PhysicsSpace): PlayerStrategy
}

class NoOpPlayerStrategy : PlayerStrategy {
    override fun destroy() {}

    override fun enable() {}

    override fun disable() {}

    override fun tickUpdate() {}

    override fun physicsUpdate(deltaTime: Float) {}
}
