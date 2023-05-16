package io.github.aecsocket.ignacio

import java.util.function.Consumer

interface Shape : Destroyable

enum class CoeffCombineRule {
    AVERAGE,
    MIN,
    MULTIPLY,
    MAX,
}

interface PhysicsMaterial : Destroyable {
    val friction: Real

    val restitution: Real

    val frictionCombine: CoeffCombineRule

    val restitutionCombine: CoeffCombineRule
}

interface Collider : Destroyable {
    fun <R> read(block: (Read) -> R): R

    fun read(block: Consumer<Read>) = read { block.accept(it) }

    fun <R> write(block: (Write) -> R): R

    fun write(block: Consumer<Write>) = write { block.accept(it) }

    fun addTo(space: PhysicsSpace)

    fun remove()

    fun attachTo(parent: RigidBody)

    fun detach()

    interface Access {
        val handle: Collider

        val shape: Shape

        val material: PhysicsMaterial

        val position: Iso

        val isSensor: Boolean

        val relativePosition: Iso

        val parent: RigidBody?
    }

    interface Read : Access

    interface Write : Access {
        override var shape: Shape

        override var material: PhysicsMaterial

        override var position: Iso

        override var isSensor: Boolean

        override var relativePosition: Iso
    }
}
