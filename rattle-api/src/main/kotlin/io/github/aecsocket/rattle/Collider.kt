package io.github.aecsocket.rattle

import java.util.function.Consumer

interface Shape : Destroyable

enum class CoeffCombineRule {
    AVERAGE,
    MIN,
    MULTIPLY,
    MAX,
}

interface PhysicsMaterial {
    val friction: Real

    val restitution: Real

    val frictionCombine: CoeffCombineRule

    val restitutionCombine: CoeffCombineRule
}

sealed interface Mass {
    data class Density(val density: Real) : Mass {
        init {
            require(density > 0.0) { "requires density > 0.0" }
        }
    }

    data class Constant(val mass: Real) : Mass {
        init {
            require(mass > 0.0) { "requires mass > 0.0" }
        }
    }
}

interface Collider : Destroyable {
    fun <R> read(block: (Read) -> R): R

    fun read(block: Consumer<Read>) = read { block.accept(it) }

    fun <R> write(block: (Write) -> R): R

    fun write(block: Consumer<Write>) = write { block.accept(it) }

    fun addTo(space: PhysicsSpace)

    fun remove()

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

        override var parent: RigidBody?
    }
}
