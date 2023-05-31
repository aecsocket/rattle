package io.github.aecsocket.rattle

enum class Visibility {
    VISIBLE,
    INVISIBLE,
}

sealed interface PrimitiveBodyDesc {
    val geom: Geometry
    val material: PhysicsMaterial
    val mass: Mass
    val visibility: Visibility

    data class Fixed(
        override val geom: Geometry,
        override val material: PhysicsMaterial,
        override val mass: Mass = Mass.Density(1.0),
        override val visibility: Visibility = Visibility.VISIBLE,
    ) : PrimitiveBodyDesc

    data class Moving(
        override val geom: Geometry,
        override val material: PhysicsMaterial,
        override val mass: Mass = Mass.Density(1.0),
        override val visibility: Visibility = Visibility.VISIBLE,
        val isCcdEnabled: Boolean = false,
        val gravityScale: Real = 1.0,
        val linearDamping: Real = DEFAULT_LINEAR_DAMPING,
        val angularDamping: Real = DEFAULT_ANGULAR_DAMPING,
    ) : PrimitiveBodyDesc
}

interface PrimitiveBodies<W> {
    val count: Int

    fun create(location: Location<W>, desc: PrimitiveBodyDesc)

    fun destroyAll()

    fun onTick()

    fun onPhysicsStep()
}
