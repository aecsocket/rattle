package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.ignacio.DestroyFlag
import io.github.aecsocket.ignacio.Destroyable
import io.github.aecsocket.ignacio.PhysicsBody
import io.github.aecsocket.ignacio.PhysicsSpace
import org.bukkit.Chunk
import org.bukkit.World


interface TerrainStrategy : Destroyable {
     fun enable()

     fun disable()

     fun isTerrain(body: PhysicsBody.Read): Boolean

     fun onChunksLoad(chunks: Collection<Chunk>)

     fun onChunksUnload(chunks: Collection<Chunk>)
}

object NoOpTerrainStrategy : TerrainStrategy {
    override fun destroy() {}

    override fun enable() {}

    override fun disable() {}

    override fun isTerrain(body: PhysicsBody.Read) = false

    override fun onChunksLoad(chunks: Collection<Chunk>) {}

    override fun onChunksUnload(chunks: Collection<Chunk>) {}
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
