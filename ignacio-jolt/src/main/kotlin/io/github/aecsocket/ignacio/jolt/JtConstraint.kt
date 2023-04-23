package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.Constraint
import io.github.aecsocket.ignacio.ConstraintTarget
import io.github.aecsocket.ignacio.PhysicsBody
import jolt.physics.body.Body
import jolt.physics.body.MutableBody
import java.util.concurrent.atomic.AtomicBoolean

data class JtConstraint internal constructor(val handle: jolt.physics.constraint.Constraint) : Constraint {
    val isDestroyed = AtomicBoolean(false)

    override var isEnabled: Boolean
        get() = handle.enabled
        set(value) {
            handle.enabled = value
        }

    override fun toString() = "Constraint(${handle.subType})"
}

fun ConstraintTarget.asBody(): MutableBody = when (this) {
    is ConstraintTarget.World -> Body.fixedToWorld()
    is PhysicsBody.Write -> (this as JtPhysicsBody.Write).body
}
