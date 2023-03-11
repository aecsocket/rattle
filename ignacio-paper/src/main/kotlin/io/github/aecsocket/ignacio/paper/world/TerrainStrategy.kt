package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.math.Point3
import org.bukkit.Chunk
import org.bukkit.World

interface TerrainStrategy : PhysicsWorldHook, Destroyable {
    val numInFluid: Int

    fun isTerrain(body: PhysicsBody): Boolean

    fun onChunksLoad(chunks: Collection<Chunk>)

    fun onChunksUnload(chunks: Collection<Chunk>)

    fun onSlicesUpdate(slices: Collection<SlicePos>)
}

fun interface TerrainStrategyFactory {
    fun create(engine: IgnacioEngine, world: World, physics: PhysicsSpace): TerrainStrategy
}

class NoOpTerrainStrategy : TerrainStrategy {
    override val numInFluid get() = 0

    override fun destroy() {}

    override fun enable() {}

    override fun disable() {}

    override fun tickUpdate() {}

    override fun physicsUpdate(deltaTime: Float) {}

    override fun isTerrain(body: PhysicsBody) = false

    override fun onChunksLoad(chunks: Collection<Chunk>) {}

    override fun onChunksUnload(chunks: Collection<Chunk>) {}

    override fun onSlicesUpdate(slices: Collection<SlicePos>) {}
}
