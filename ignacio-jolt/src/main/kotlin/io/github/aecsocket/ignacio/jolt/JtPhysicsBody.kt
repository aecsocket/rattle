package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.core.DynamicBody
import io.github.aecsocket.ignacio.core.PhysicsBody
import io.github.aecsocket.ignacio.core.StaticBody
import io.github.aecsocket.ignacio.core.math.Transform
import jolt.kotlin.*
import jolt.math.JtQuat
import jolt.math.JtVec3d
import jolt.physics.Activation
import jolt.physics.collision.shape.Shape

sealed class JtPhysicsBody(
    private val engine: JoltEngine,
    val space: JtPhysicsSpace,
    val id: BodyId,
    val shape: Shape,
) : PhysicsBody {
    override fun destroy() {
        if (space.handle.bodyInterface.isAdded(id))
            throw IllegalStateException("Body is still added to space")
        space.handle.bodyInterface.destroyBody(id)
        shape.delete()
    }

    override var transform: Transform
        get() {
            val position = JtVec3d()
            val rotation = JtQuat()
            space.handle.bodyInterface.getPositionAndRotationDp(id, position, rotation)
            return Transform(position.ignacio(), rotation.ignacio())
        }
        set(value) {
            space.handle.bodyInterface.setPositionAndRotationDp(id,
                value.position.jolt(), value.rotation.jolt(),
                Activation.DONT_ACTIVATE
            )
        }
}

class JtStaticBody(
    engine: JoltEngine, space: JtPhysicsSpace, id: BodyId, shape: Shape
) : JtPhysicsBody(engine, space, id, shape), StaticBody {
    override fun toString() = "JtStaticBody(${id.id})"
}

class JtDynamicBody(
    engine: JoltEngine, space: JtPhysicsSpace, id: BodyId, shape: Shape
) : JtPhysicsBody(engine, space, id, shape), DynamicBody {
    override fun toString() = "JtDynamicBody(${id.id})"
}
