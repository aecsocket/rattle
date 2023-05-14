package io.github.aecsocket.ignacio

import org.spongepowered.configurate.objectmapping.ConfigSerializable

enum class CoeffCombineRule {
    AVERAGE,
    MIN,
    MULTIPLY,
    MAX,
}

@ConfigSerializable
data class PhysicsMaterialDesc(
    val friction: Real = DEFAULT_FRICTION,
    val restitution: Real = DEFAULT_RESTITUTION,
    val frictionCombine: CoeffCombineRule = CoeffCombineRule.AVERAGE,
    val restitutionCombine: CoeffCombineRule = CoeffCombineRule.AVERAGE,
)

interface PhysicsMaterial : Destroyable
