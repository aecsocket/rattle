package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.CoeffCombineRule
import io.github.aecsocket.ignacio.PhysicsMaterial
import io.github.aecsocket.ignacio.Real

class JoltMaterial internal constructor(
    val friction: Real,
    val restitution: Real,
    val frictionCombine: CoeffCombineRule,
    val restitutionCombine: CoeffCombineRule,
) : PhysicsMaterial {
    override fun destroy() {}
}
