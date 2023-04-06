package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.ignacio.*
import io.github.aecsocket.ignacio.paper.Ignacio
import io.github.aecsocket.ignacio.paper.position
import io.github.aecsocket.klam.IVec3
import org.bukkit.Chunk
import org.bukkit.World
import org.bukkit.block.Block

sealed interface BlockUpdate {
    val position: IVec3

    data class Remove(
        override val position: IVec3,
    ) : BlockUpdate

    data class Set(
        val block: Block,
    ) : BlockUpdate {
        override val position = block.position()
    }
}

interface TerrainStrategy : Destroyable {
     fun enable()

     fun disable()

     fun isTerrain(body: PhysicsBody.Read): Boolean

     fun onPhysicsUpdate(deltaTime: Float)

     fun onChunksLoad(chunks: Collection<Chunk>)

     fun onChunksUnload(chunks: Collection<Chunk>)

     fun onBlockUpdate(update: BlockUpdate)
}

fun interface TerrainStrategyFactory {
    fun create(ignacio: Ignacio, world: World, physics: PhysicsSpace): TerrainStrategy
}

object NoOpTerrainStrategy : TerrainStrategy {
    override fun destroy() {}

    override fun enable() {}

    override fun disable() {}

    override fun isTerrain(body: PhysicsBody.Read) = false

    override fun onPhysicsUpdate(deltaTime: Float) {}

    override fun onChunksLoad(chunks: Collection<Chunk>) {}

    override fun onChunksUnload(chunks: Collection<Chunk>) {}

    override fun onBlockUpdate(update: BlockUpdate) {}
}

interface EntityStrategy : Destroyable {

}

fun interface EntityStrategyFactory {
    fun create(ignacio: Ignacio, world: World, physics: PhysicsSpace): EntityStrategy
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
        terrain.onPhysicsUpdate(nextDeltaTime)
        physics.update(nextDeltaTime)
    }
}
