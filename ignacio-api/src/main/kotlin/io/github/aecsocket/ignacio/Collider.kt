package io.github.aecsocket.ignacio

data class ColliderDesc(
    val shape: Shape,
    val position: Iso = Iso(),
    val friction: Real = DEFAULT_FRICTION,
    val restitution: Real = DEFAULT_RESTITUTION,
)


interface Collider : Destroyable {
    fun detachFromParent()

    fun attachTo(parent: RigidBody)
}
