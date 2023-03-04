package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.math.*
import org.bukkit.Chunk
import org.bukkit.World
import java.util.concurrent.ConcurrentHashMap

class WorldPhysics internal constructor(
    private val ignacio: Ignacio,
    val world: World,
    val physics: PhysicsSpace,
) {
    private val numSlices = world.maxHeight / 16
    private val startY = world.minHeight

    data class SliceData(
        val body: BodyAccess
    )

    val terrain = ConcurrentHashMap<Long, Array<SliceData?>>()
    private var destroyed = false

    operator fun component1() = physics

    fun load(chunk: Chunk) {
        if (!ignacio.settings.terrain.autogenerate) return
        if (destroyed) return

        val chunkKey = chunk.chunkKey
        if (terrain.containsKey(chunkKey)) return

        val chunkCoord = "(${chunk.x}, ${chunk.z})"
        val snapshot = chunk.getChunkSnapshot(false, false, false)
        ignacio.engine.runTask {
            val chunkBase = Vec3d(chunk.x * 16.0, 0.0, chunk.z * 16.0)
            val slices = Array(numSlices) { slice ->
                val sliceBase = chunkBase.copy(y = startY + slice * 16.0)
                val sliceChildren = ArrayList<CompoundChild>()

                fun process(lx: Int, ly: Int, lz: Int) {
                    val gx = chunk.x * 16 + lx
                    val gy = startY + slice * 16 + ly
                    val gz = chunk.z * 16 + lz

                    val block = snapshot.getBlockData(lx, gy, lz)
                    val blockOrigin = Vec3d(gx.toDouble(), gy.toDouble(), gz.toDouble())

                    if (block.material.isCollidable) {
                        sliceChildren += CompoundChild(
                            Vec3f(lx + 0.5f, ly + 0.5f, lz + 0.5f),
                            Quat.Identity,
                            BoxGeometry(Vec3f(0.5f))
                        )
                    }
                }

                (0 until 16).forEach { localX ->
                    (0 until 16).forEach { localY ->
                        (0 until 16).forEach { localZ ->
                            process(localX, localY, localZ)
                        }
                    }
                }

                if (sliceChildren.isNotEmpty()) {
                    val settings = StaticBodySettings(
                        geometry = StaticCompoundGeometry(sliceChildren)
                    )
                    SliceData(
                        body = physics.bodies.addStatic(settings, Transform(sliceBase))
                    )
                } else null
            }

            terrain[chunkKey] = slices
        }
    }

    fun unload(chunk: Chunk) {
        if (destroyed) return

        val chunkKey = chunk.chunkKey
        val slices = terrain[chunkKey] ?: return
        slices.forEach { slice ->
            if (slice == null) return@forEach
            physics.bodies.destroy(slice.body)
        }
        terrain.remove(chunkKey)
    }

    internal fun destroy() {
        destroyed = true
        terrain.forEach terrain@ { (_, slices) ->
            slices.forEach { slice ->
                if (slice == null) return@forEach
                physics.bodies.destroy(slice.body)
            }
        }
        terrain.clear()
        ignacio.engine.destroySpace(physics)
    }
}


/*val boundingBoxes = chunk.getBlock(lx, gy, lz).collisionShape.boundingBoxes.toList()
    .map { box -> AABB(box.min.vec3d() - blockOrigin, box.max.vec3d() - blockOrigin) }

when {
    block.material.isCollidable -> {
        if (boundingBoxes.isEmpty()) return

        val blockChildren = boundingBoxes.map { box ->
            CompoundChild(
                box.center().sp(),
                Quat.Identity,
                BoxGeometry(box.halfExtent().sp()),
            )
        }

        sliceChildren += when (blockChildren.size) {
            1 -> {
                val child = blockChildren[0]
                CompoundChild(
                    Vec3f(lx.toFloat(), ly.toFloat(), lz.toFloat()) + child.position,
                    child.rotation,
                    child.geometry,
                )
            }
            else -> {
                CompoundChild(
                    Vec3f(lx.toFloat(), ly.toFloat(), lz.toFloat()),
                    Quat.Identity,
                    StaticCompoundGeometry(blockChildren),
                )
            }
        }
    }
    block.material == Material.WATER -> {
        // todo buoyancy
    }
}*/
