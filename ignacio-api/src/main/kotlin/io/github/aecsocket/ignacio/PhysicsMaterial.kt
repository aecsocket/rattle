package io.github.aecsocket.ignacio

enum class CoeffCombineRule {
    AVERAGE,
    MIN,
    MULTIPLY,
    MAX,
}

interface PhysicsMaterial : Destroyable
