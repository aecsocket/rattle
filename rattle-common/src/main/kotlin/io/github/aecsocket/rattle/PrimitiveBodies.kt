package io.github.aecsocket.rattle

enum class Visibility {
    VISIBLE,
    INVISIBLE,
}

interface PrimitiveBodies<W> {
    val count: Int

    fun create(
        world: W,
        geom: Geometry,
        body: RigidBody,
        collider: Collider,
        visibility: Visibility,
    )

    fun destroyAll()

    fun onTick()

    fun onPhysicsStep()
}
