package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.ignacio.core.DestroyFlag
import io.github.aecsocket.ignacio.core.Destroyable
import io.github.aecsocket.ignacio.core.PhysicsSpace
import org.bukkit.Chunk
import org.bukkit.World

data class ChunkSlice(val x: Int, val y: Int, val z: Int)

class PhysicsWorld(
    val world: World,
    val physics: PhysicsSpace,
    terrainStrategy: TerrainStrategy,
    entityStrategy: EntityStrategy,
) : Destroyable {
    interface Terrain {
        val strategy: TerrainStrategy
    }

    interface Entities {
        val strategy: EntityStrategy
    }

    private val destroyed = DestroyFlag()
    val terrain = object : Terrain {
        override val strategy = terrainStrategy
    }
    val entities = object : Entities {
        override val strategy = entityStrategy


    }

    fun loadChunks(chunk: Collection<Chunk>) {
        terrain.strategy.loadChunks(chunk)
    }

    fun unloadChunks(chunks: Collection<Chunk>) {
        terrain.strategy.unloadChunks(chunks)
    }

    override fun destroy() {
        destroyed.mark()
        terrain.strategy.destroy()
        entities.strategy.destroy()
    }

    operator fun component1() = physics
}
