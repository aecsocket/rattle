package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.math.Vec3f
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

    private val inWater = HashSet<BodyRef>()
    private val stepListener = StepListener { deltaTime ->
        inWater.forEach { bodyRef ->
            bodyRef.writeUnlockedOf<BodyRef.MovingWrite> { body ->
                body.applyBuoyancy(
                    deltaTime = deltaTime,
                    buoyancy = 1.5f,
                    fluid = FluidSettings(
                        surfacePosition = Vec3f(0.0f, 63.0f, 0.0f),
                        surfaceNormal = Vec3f.Up,
                        linearDrag = 0.05f,
                        angularDrag = 0.01f,
                        velocity = Vec3f.Zero,
                    )
                )
            }
        }
    }
    private val contactListener = object : ContactListener {
        override fun onAdded(body1: BodyRef.Read, body2: BodyRef.Read) {
            (terrain.strategy.terrainData(body1.ref) as? TerrainData.Fluid)?.let {
                inWater += body2.ref
            }
            (terrain.strategy.terrainData(body2.ref) as? TerrainData.Fluid)?.let {
                inWater += body1.ref
            }
        }

        override fun onRemoved(body1: BodyRef, body2: BodyRef) {}
    }

    init {
        physics.onStep(stepListener)
        physics.onContact(contactListener)
    }

    fun loadChunks(chunk: Collection<Chunk>) {
        terrain.strategy.loadChunks(chunk)
    }

    fun unloadChunks(chunks: Collection<Chunk>) {
        terrain.strategy.unloadChunks(chunks)
    }

    override fun destroy() {
        destroyed.mark()
        physics.removeStepListener(stepListener)
        terrain.strategy.destroy()
        entities.strategy.destroy()
    }

    operator fun component1() = physics
}
