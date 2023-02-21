package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.ignacio.core.PhysicsBody
import io.github.aecsocket.ignacio.core.PhysicsSpace
import io.github.aecsocket.ignacio.paper.display.WorldRender
import io.github.aecsocket.ignacio.paper.display.despawn
import io.github.aecsocket.ignacio.paper.display.transform
import org.bukkit.entity.Entity

class PrimitiveBodies internal constructor() {
    private data class Instance(
        val physics: PhysicsSpace,
        val body: PhysicsBody,
        val entity: Entity,
        val render: WorldRender?,
    )

    private val bodies = ArrayList<Instance>()

    fun create(physics: PhysicsSpace, body: PhysicsBody, entity: Entity, render: WorldRender?) {
        bodies += Instance(physics, body, entity, render)
    }

    internal fun update() {
        bodies.forEach { instance ->
            instance.render?.transform(instance.body.transform)
        }
    }

    fun numBodies() = bodies.size

    fun removeAll() {
        bodies.forEach { instance ->
            instance.physics.removeBody(instance.body)
            instance.body.destroy()
            instance.render?.despawn()
            instance.entity.remove()
        }
        bodies.clear()
    }
}
