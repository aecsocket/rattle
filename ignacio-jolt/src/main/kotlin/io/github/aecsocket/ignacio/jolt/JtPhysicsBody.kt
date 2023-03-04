package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.core.BodyAccess
import io.github.aecsocket.ignacio.core.DynamicBodyAccess
import io.github.aecsocket.ignacio.core.StaticBodyAccess
import io.github.aecsocket.ignacio.core.math.Transform
import jolt.kotlin.BodyId
import jolt.physics.Activation
import jolt.physics.PhysicsSystem

open class JtBodyAccess(
    val system: PhysicsSystem,
    val id: BodyId,
) : BodyAccess {
    override fun toString() = "${id.id}"

    override var transform: Transform
        get() {
            return useArena {
                val position = DVec3()
                val rotation = JQuat()
                system.bodyInterface.getPositionAndRotation(id.id, position, rotation)
                Transform(position.toIgnacio(), rotation.toIgnacio())
            }
        }
        set(value) {
            useArena {
                system.bodyInterface.setPositionAndRotation(id.id,
                    value.position.toJolt(), value.rotation.toJolt(),
                    Activation.DONT_ACTIVATE
                )
            }
        }

    override fun asStatic() = JtStaticBodyAccess(system, id)

    override fun asDynamic() = JtDynamicBodyAccess(system, id)
}

class JtStaticBodyAccess internal constructor(
    system: PhysicsSystem,
    id: BodyId
) : JtBodyAccess(system, id), StaticBodyAccess

class JtDynamicBodyAccess internal constructor(
    system: PhysicsSystem,
    id: BodyId
) : JtBodyAccess(system, id), DynamicBodyAccess
