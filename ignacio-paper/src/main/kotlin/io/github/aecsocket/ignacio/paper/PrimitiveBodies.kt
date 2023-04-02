package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.ignacio.PhysicsBody
import io.github.aecsocket.ignacio.PhysicsSpace
import io.github.aecsocket.ignacio.Transform
import io.github.aecsocket.ignacio.paper.render.Render
import org.bukkit.World
import org.bukkit.entity.Entity

class PrimitiveBodies internal constructor(private val ignacio: Ignacio) {
    private data class Instance(
        val physics: PhysicsSpace,
        val body: PhysicsBody,
        val render: Render?,
    )

    private val bodies = ArrayList<Instance>()
    private val markerToBody = HashMap<Entity, Instance>()

    val size get() = bodies.size

    fun create(
        world: World,
        transform: Transform,

    ): Int {
//        val marker = spawnMarkerEntity(transform.position.location(world))
//        val bodyId = bodies.size
//        bodies += Instance()
//        bodies[marker] = Instance()
        TODO()
    }

    fun destroyAll() {

    }
}
