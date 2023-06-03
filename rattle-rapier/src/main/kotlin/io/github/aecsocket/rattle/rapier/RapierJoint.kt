package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.rattle.DestroyFlag
import io.github.aecsocket.rattle.ImpulseJoint
import io.github.aecsocket.rattle.MultibodyJoint
import rapier.data.ArenaKey

data class RapierImpulseJointKey(val id: Long) : ImpulseJoint {
    override fun toString(): String = ArenaKey.asString(id)
}

data class RapierMultibodyJointKey(val id: Long) : MultibodyJoint {
    override fun toString(): String = ArenaKey.asString(id)
}

class RapierJoint internal constructor(
) : ImpulseJoint, MultibodyJoint {
}
