package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.ignacio.*
import org.bukkit.Chunk
import org.bukkit.World


interface TerrainStrategy : Destroyable {
     fun enable()

     fun disable()

     fun isTerrain(body: PhysicsBody.Read): Boolean

     fun physicsUpdate(deltaTime: Float)

     fun syncUpdate()

     fun onChunksLoad(chunks: Collection<Chunk>)

     fun onChunksUnload(chunks: Collection<Chunk>)
}

fun interface TerrainStrategyFactory {
    fun create(engine: IgnacioEngine, world: World, physics: PhysicsSpace): TerrainStrategy
}

object NoOpTerrainStrategy : TerrainStrategy {
    override fun destroy() {}

    override fun enable() {}

    override fun disable() {}

    override fun isTerrain(body: PhysicsBody.Read) = false

    override fun physicsUpdate(deltaTime: Float) {}

    override fun syncUpdate() {}

    override fun onChunksLoad(chunks: Collection<Chunk>) {}

    override fun onChunksUnload(chunks: Collection<Chunk>) {}
}

interface EntityStrategy : Destroyable {

}

fun interface EntityStrategyFactory {
    fun create(engine: IgnacioEngine, world: World, physics: PhysicsSpace): EntityStrategy
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

    fun startPhysicsUpdate(deltaTime: Float) {
        nextDeltaTime = deltaTime
    }

    fun joinPhysicsUpdate() {
        physics.update(nextDeltaTime)
        terrain.physicsUpdate(nextDeltaTime)
    }

    fun syncUpdate() {
        terrain.syncUpdate()
    }
}
