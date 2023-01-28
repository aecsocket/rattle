package io.gitlab.aecsocket.ignacio.paper

import io.gitlab.aecsocket.ignacio.core.*
import io.gitlab.aecsocket.ignacio.core.math.Transform
import io.gitlab.aecsocket.ignacio.core.math.Vec3
import org.bukkit.Material
import org.bukkit.World

class SimpleTerrainManager(
    private val backend: IgBackend<*>,
    private val world: World,
    private val physicsSpace: IgPhysicsSpace
) {
    private data class TileData(
        val type: Material,
        val shapeFactory: () -> Collection<IgShape>
    ) {
        fun sameType(o: TileData) = type == o.type
    }

    private data class TileInstance(
        val data: TileData,
        var body: IgBody?
    )

    private val terrain = HashMap<TerrainPos, TileInstance>()

    private fun tileDataOf(pos: TerrainPos): TileData {
        val block = world.getBlockAt(pos.x, pos.y, pos.z)
        return TileData(block.type) {
            val shape = block.collisionShape
            val boxes = shape.boundingBoxes
            if (boxes.isEmpty()) return@TileData emptyList()

            boxes.map { box ->
                val extent = box.max.subtract(box.min)
                backend.createShape(
                    IgBoxGeometry(extent.multiply(0.5).ig()),
                    Transform(box.center.ig())
                )
            }
        }
    }

    private fun addBodyOf(data: TileData, pos: TerrainPos): IgBody? {
        val shapes = data.shapeFactory()
        if (shapes.isEmpty()) return null
        val body = backend.createStaticBody(
            Transform(Vec3(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble()))
        )
        shapes.forEach { body.attachShape(it) }
        physicsSpace.addBody(body)
        return body
    }

    fun compute(positions: Iterable<TerrainPos>) {
        positions.forEach { pos ->
            val newTile = tileDataOf(pos)
            terrain[pos]?.let { oldTile ->
                if (!oldTile.data.sameType(newTile)) {
                    oldTile.body?.let {
                        physicsSpace.removeBody(it)
                        it.destroy()
                    }
                    val newBody = addBodyOf(newTile, pos)
                    oldTile.body = newBody
                }
            } ?: run {
                val newBody = addBodyOf(newTile, pos)
                terrain[pos] = TileInstance(newTile, newBody)
            }
        }

        val iter = terrain.iterator()
        while (iter.hasNext()) {
            val (pos, tile) = iter.next()
            if (!positions.contains(pos)) {
                tile.body?.let {
                    physicsSpace.removeBody(it)
                    it.destroy()
                }
                iter.remove()
            }
        }
    }
}
