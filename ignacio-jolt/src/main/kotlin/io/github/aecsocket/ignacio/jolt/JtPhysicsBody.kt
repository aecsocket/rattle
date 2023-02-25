package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.core.BodyAccess
import io.github.aecsocket.ignacio.core.DynamicBodyAccess
import io.github.aecsocket.ignacio.core.StaticBodyAccess
import io.github.aecsocket.ignacio.core.math.Transform
import jolt.kotlin.BodyID
import jolt.kotlin.getPositionAndRotationDp
import jolt.kotlin.setPositionAndRotationDp
import jolt.math.JtQuat
import jolt.math.JtVec3d
import jolt.physics.Activation
import jolt.physics.PhysicsSystem

open class JtBodyAccess(
    val system: PhysicsSystem,
    val id: BodyID,
) : BodyAccess {
    override fun toString() = "${id.id}"

    override var transform: Transform
        get() {
            val position = JtVec3d()
            val rotation = JtQuat()
            system.bodyInterface.getPositionAndRotationDp(id, position, rotation)
            return Transform(position.ignacio(), rotation.ignacio())
        }
        set(value) {
            system.bodyInterface.setPositionAndRotationDp(id, value.position.jolt(), value.rotation.jolt(), Activation.DONT_ACTIVATE)
        }

    override fun asStatic() = JtStaticBodyAccess(system, id)

    override fun asDynamic() = JtDynamicBodyAccess(system, id)
}

class JtStaticBodyAccess internal constructor(
    system: PhysicsSystem,
    id: BodyID
) : JtBodyAccess(system, id), StaticBodyAccess

class JtDynamicBodyAccess internal constructor(
    system: PhysicsSystem,
    id: BodyID
) : JtBodyAccess(system, id), DynamicBodyAccess
