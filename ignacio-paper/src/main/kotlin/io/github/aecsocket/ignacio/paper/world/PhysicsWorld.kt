package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.ignacio.core.*
import org.bukkit.World

class PhysicsWorld(
    val world: World,
    val physics: PhysicsSpace,
    val terrain: TerrainStrategy,
    val entities: EntityStrategy,
) : Destroyable {
    private val destroyed = DestroyFlag()

    fun update(deltaTime: Float) {
        physics.update(deltaTime)
    }

    override fun destroy() {
        destroyed.mark()
        terrain.destroy()
        entities.destroy()
    }

    operator fun component1() = physics
}
