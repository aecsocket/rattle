package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.rattle.DestroyFlag
import io.github.aecsocket.rattle.ImpulseJoint
import io.github.aecsocket.rattle.MultibodyJoint
import rapier.data.ArenaKey

@JvmInline
value class JointHandle(val id: Long) {
    override fun toString(): String = ArenaKey.asString(id)
}

class RapierJoint internal constructor(
    var state: State,
) : ImpulseJoint, MultibodyJoint {
    sealed interface State {
        data class Removed(
            val joint: rapier.dynamics.joint.GenericJoint.Mut,
        ) : State

        data class Impulse(
            val space: RapierSpace,
            val handle: JointHandle,
        ) : State

        data class Multibody(
            val space: RapierSpace,
            val handle: JointHandle,
        ) : State
    }

    private val destroyed = DestroyFlag()

    override fun destroy() {
        when (val state = state) {
            is State.Removed -> {
                destroyed()
                state.joint.drop()
            }
            is State.Impulse -> throw IllegalStateException("$this is still added to ${state.space} as an impulse joint")
            is State.Multibody -> throw IllegalStateException("$this is still added to ${state.space} as a multibody joint")
        }
    }
}
