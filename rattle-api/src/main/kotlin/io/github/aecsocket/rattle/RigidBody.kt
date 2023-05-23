package io.github.aecsocket.rattle

import java.util.function.Consumer

sealed interface Sleeping {
    /* TODO Kotlin 1.9: data */ object Disabled : Sleeping

    data class Enabled(val state: Boolean) : Sleeping
}

interface RigidBody {
    fun <R> readBody(block: (Read) -> R): R

    fun readBody(block: Consumer<Read>) = readBody { block.accept(it) }

    fun <R> writeBody(block: (Write) -> R): R

    fun writeBody(block: Consumer<Write>) = writeBody { block.accept(it) }

    fun addTo(space: PhysicsSpace)

    fun remove()

    interface Access {
        val handle: RigidBody

        val position: Iso
    }

    interface Read : Access

    interface Write : Access {
        override var position: Iso
    }
}

interface FixedBody : RigidBody {
    fun <R> readFixed(block: (Read) -> R): R

    fun <R> writeFixed(block: (Write) -> R): R

    interface Access : RigidBody.Access {
        override val handle: FixedBody
    }

    interface Read : Access, RigidBody.Read

    interface Write : Access, RigidBody.Write
}

interface MovingBody : RigidBody {
    fun <R> readMoving(block: (Read) -> R): R

    fun readMoving(block: Consumer<Read>) = readMoving { block.accept(it) }

    fun <R> writeMoving(block: (Write) -> R): R

    fun writeMoving(block: Consumer<Write>) = writeMoving { block.accept(it) }

    interface Access : RigidBody.Access {
        override val handle: MovingBody

        val isKinematic: Boolean

        val isCcdEnabled: Boolean

        val linearVelocity: Vec

        val angularVelocity: Vec

        val gravityScale: Real

        val linearDamping: Real

        val angularDamping: Real

        val isSleeping: Boolean

        val kineticEnergy: Real

        val appliedForce: Vec

        val appliedTorque: Vec
    }

    interface Read : Access, RigidBody.Read

    interface Write : Access, RigidBody.Write {
        override var isKinematic: Boolean

        override var isCcdEnabled: Boolean

        override var linearVelocity: Vec

        override var angularVelocity: Vec

        override var gravityScale: Real

        override var linearDamping: Real

        override var angularDamping: Real

        fun sleep()

        fun wakeUp(strong: Boolean)

        fun applyForce(force: Vec)

        fun applyForceAt(force: Vec, at: Vec)

        fun applyImpulse(impulse: Vec)

        fun applyImpulseAt(impulse: Vec, at: Vec)

        fun applyTorque(torque: Vec)

        fun applyTorqueImpulse(torqueImpulse: Vec)

        fun kinematicTarget(position: Iso)
    }
}
