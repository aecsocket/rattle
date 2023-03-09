package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.math.Point3
import org.bukkit.Chunk
import org.bukkit.World

typealias BlockPos = Point3

interface TerrainStrategy : PhysicsUpdatable, Destroyable {
    fun isTerrain(body: PhysicsBody): Boolean

    fun onChunksLoad(chunks: Collection<Chunk>)

    fun onChunksUnload(chunks: Collection<Chunk>)

    fun onBlocksUpdate(blocks: Collection<BlockPos>)
}

fun interface TerrainStrategyFactory {
    fun create(engine: IgnacioEngine, world: World, physics: PhysicsSpace): TerrainStrategy
}

class NoOpTerrainStrategy : TerrainStrategy {
    override fun destroy() {}

    override fun physicsUpdate(deltaTime: Float) {}

    override fun tickUpdate() {}

    override fun isTerrain(body: PhysicsBody) = false

    override fun onChunksLoad(chunks: Collection<Chunk>) {}

    override fun onChunksUnload(chunks: Collection<Chunk>) {}

    override fun onBlocksUpdate(blocks: Collection<BlockPos>) {}
}
