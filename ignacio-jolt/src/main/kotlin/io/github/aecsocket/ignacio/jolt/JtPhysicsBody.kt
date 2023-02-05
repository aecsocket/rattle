package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.core.DynamicBody
import io.github.aecsocket.ignacio.core.PhysicsBody
import io.github.aecsocket.ignacio.core.StaticBody
import io.github.aecsocket.ignacio.core.math.Transform
import jolt.kotlin.*
import jolt.math.JtQuat
import jolt.math.JtVec3d
import jolt.physics.Activation

sealed class JtPhysicsBody(
    private val engine: JoltEngine,
    val space: JtPhysicsSpace,
    val id: BodyId
) : PhysicsBody {
    override fun destroy() {
        space.handle.bodyInterface.apply {
            removeBody(id)
            destroyBody(id)
        }
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
    engine: JoltEngine, space: JtPhysicsSpace, id: BodyId
) : JtPhysicsBody(engine, space, id), StaticBody

class JtDynamicBody(
    engine: JoltEngine, space: JtPhysicsSpace, id: BodyId
) : JtPhysicsBody(engine, space, id), DynamicBody
