package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.ignacio.DestroyFlag
import io.github.aecsocket.ignacio.Destroyable
import io.github.aecsocket.ignacio.IgnacioEngine
import io.github.aecsocket.ignacio.PhysicsSpace
import org.bukkit.World

interface TerrainStrategy : Destroyable {

}

object NoOpTerrainStrategy : TerrainStrategy {
    override fun destroy() {}
}

interface EntityStrategy : Destroyable {

}

object NoOpEntityStrategy : EntityStrategy {
    override fun destroy() {}
}

class PhysicsWorld(
    val world: World,
    val physics: PhysicsSpace,
    val terrain: TerrainStrategy,
    val entities: EntityStrategy,
) : Destroyable {
    private val destroyed = DestroyFlag()
    private var nextDeltaTime = 0f

    override fun destroy() {
        destroyed.mark()
        terrain.destroy()
        entities.destroy()
    }

    operator fun component1() = physics

    internal fun startPhysicsUpdate(deltaTime: Float) {
        nextDeltaTime = deltaTime
    }

    internal fun joinPhysicsUpdate() {
        physics.update(nextDeltaTime)
    }
}
