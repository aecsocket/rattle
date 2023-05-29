package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.alexandria.Render
import io.github.aecsocket.rattle.*
import net.minecraft.server.level.ServerLevel

class FabricPrimitiveBodies : PrimitiveBodies<ServerLevel> {
    data class Instance(
        val body: RigidBody,
        val collider: Collider,
        val render: Render?,
    )

    private val instances = ArrayList<Instance>()

    override val count: Int
        get() = instances.size

    override fun create(
        world: ServerLevel,
        geom: Geometry,
        body: RigidBody,
        collider: Collider,
        visibility: Visibility,
    ) {
        val render = when (visibility) {
            Visibility.VISIBLE -> {
            }
            Visibility.INVISIBLE -> null
        }

        world.physicsOrCreate().withLock { (physics) ->
            body.addTo(physics)
            collider.addTo(physics)
            collider.write { coll ->
                coll.parent = body
            }
        }
    }

    override fun destroyAll() {
        instances.forEach { instance ->
            // instance.render?.despawn() // todo
        }
        instances.clear()
    }
}
