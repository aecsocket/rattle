package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.ignacio.core.*
import org.bukkit.World

interface PhysicsWorldHook {
    fun enable()

    fun disable()

    fun tickUpdate()

    fun physicsUpdate(deltaTime: Float)
}

class PhysicsWorld(
    val world: World,
    val physics: PhysicsSpace,
    val terrain: TerrainStrategy,
    val entities: EntityStrategy,
) : Destroyable {
    private val destroyed = DestroyFlag()

    fun tickUpdate() {
        terrain.tickUpdate()
        entities.tickUpdate()
    }

    fun physicsUpdate(deltaTime: Float) {
        terrain.physicsUpdate(deltaTime)
        entities.physicsUpdate(deltaTime)
        physics.update(deltaTime)
    }

    override fun destroy() {
        destroyed.mark()
        terrain.destroy()
        entities.destroy()
    }

    operator fun component1() = physics
}
