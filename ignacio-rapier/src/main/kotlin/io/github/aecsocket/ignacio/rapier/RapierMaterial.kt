package io.github.aecsocket.ignacio.rapier

import io.github.aecsocket.ignacio.PhysicsMaterial
import io.github.aecsocket.ignacio.Real
import rapier.geometry.CoefficientCombineRule

class RapierMaterial(
    val friction: Real,
    val restitution: Real,
    val frictionCombine: CoefficientCombineRule,
    val restitutionCombine: CoefficientCombineRule,
) : PhysicsMaterial {
    override fun destroy() {}
}
