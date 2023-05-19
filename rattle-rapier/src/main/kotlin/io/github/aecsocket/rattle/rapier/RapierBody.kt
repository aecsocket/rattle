package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.rattle.*
import rapier.dynamics.RigidBodyType

@JvmInline
value class RigidBodyHandle(val key: ArenaKey)

class RapierBody internal constructor(
    var state: State,
) : RigidBody, FixedBody, MovingBody {
    sealed interface State {
        data class Removed(
            val body: rapier.dynamics.RigidBody.Mut,
        ) : State

        data class Added(
            val space: RapierSpace,
            val handle: RigidBodyHandle,
        ) : State
    }

    fun <R> read(block: (RapierBody.Read) -> R): R {
        return when (val state = state) {
            is State.Removed -> block(Read(state.body))
            is State.Added -> block(Read(state.space.rigidBodySet.index(state.handle.key.id)))
        }
    }

    override fun <R> readMoving(block: (MovingBody.Read) -> R) = read(block)

    override fun <R> readFixed(block: (FixedBody.Read) -> R) = read(block)

    override fun <R> readBody(block: (RigidBody.Read) -> R) = read(block)

    fun <R> write(block: (RapierBody.Write) -> R): R {
        return when (val state = state) {
            is State.Removed -> block(Write(state.body))
            is State.Added -> block(Write(state.space.rigidBodySet.indexMut(state.handle.key.id)))
        }
    }

    override fun <R> writeMoving(block: (MovingBody.Write) -> R) = write(block)

    override fun <R> writeFixed(block: (FixedBody.Write) -> R) = write(block)

    override fun <R> writeBody(block: (RigidBody.Write) -> R) = write(block)

    override fun addTo(space: PhysicsSpace) {
        space as RapierSpace
        when (val state = state) {
            is State.Added -> throw IllegalStateException("$this is attempting to be added to $space but is already added to ${state.space}")
            is State.Removed -> {
                val handle = space.rigidBodySet.insert(state.body)
                this.state = State.Added(space, RigidBodyHandle(ArenaKey(handle)))
            }
        }
    }

    override fun remove() {
        when (val state = state) {
            is State.Added -> {
                state.space.rigidBodySet.remove(
                    state.handle.key.id,
                    state.space.islands,
                    state.space.colliderSet,
                    state.space.impulseJointSet,
                    state.space.multibodyJointSet,
                    false,
                )
            }
            is State.Removed -> throw IllegalStateException("$this is not added to a space")
        }
    }

    override fun toString() = when (val state = state) {
        is State.Added -> "RapierBody[${state.handle}]"
        is State.Removed -> "RapierBody[0x%x]".format(state.body.memory().address())
    }

    override fun equals(other: Any?) = other is RapierBody && state == other.state

    override fun hashCode() = state.hashCode()

    open inner class Access(
        internal open val body: rapier.dynamics.RigidBody,
    ) : FixedBody.Access, MovingBody.Access {
        override val handle get() = this@RapierBody

        override val position: Iso
            get() = pushArena { arena ->
                body.getPosition(arena).toIso()
            }

        override val isKinematic: Boolean
            get() = body.isKinematic

        override val isCcdEnabled: Boolean
            get() = body.isCcdEnabled

        override val linearVelocity: Vec
            get() = pushArena { arena ->
                body.getLinearVelocity(arena).toVec()
            }

        override val angularVelocity: Vec
            get() = pushArena { arena ->
                body.getAngularVelocity(arena).toVec()
            }

        override val gravityScale: Real
            get() = body.gravityScale

        override val linearDamping: Real
            get() = body.linearDamping

        override val angularDamping: Real
            get() = body.angularDamping

        override val isSleeping: Boolean
            get() = body.isSleeping

        override val kineticEnergy: Real
            get() = body.kineticEnergy

        override val appliedForce: Vec
            get() = pushArena { arena ->
                body.getUserForce(arena).toVec()
            }

        override val appliedTorque: Vec
            get() = pushArena { arena ->
                body.getUserTorque(arena).toVec()
            }
    }

    inner class Read(
        body: rapier.dynamics.RigidBody,
    ) : Access(body), FixedBody.Read, MovingBody.Read

    inner class Write(
        override val body: rapier.dynamics.RigidBody.Mut,
    ) : Access(body), FixedBody.Write, MovingBody.Write {
        override var position: Iso
            get() = super.position
            set(value) = pushArena { arena ->
                body.setPosition(value.toIsometry(arena), false)
            }

        override var isKinematic: Boolean
            get() = super.isKinematic
            set(value) {
                val oldType = body.bodyType
                val newType =
                    if (value) RigidBodyType.KINEMATIC_POSITION_BASED
                    else RigidBodyType.DYNAMIC
                if (oldType == newType) return
                body.setBodyType(newType, false)
            }

        override var isCcdEnabled: Boolean
            get() = super.isCcdEnabled
            set(value) {
                body.enableCcd(value)
            }

        override var linearVelocity: Vec
            get() = super.linearVelocity
            set(value) = pushArena { arena ->
                body.setLinearVelocity(value.toVector(arena), false)
            }

        override var angularVelocity: Vec
            get() = super.angularVelocity
            set(value) = pushArena { arena ->
                body.setAngularVelocity(value.toAngVector(arena), false)
            }

        override var gravityScale: Real
            get() = super.gravityScale
            set(value) {
                body.setGravityScale(value, false)
            }

        override var linearDamping: Real
            get() = super.linearDamping
            set(value) {
                body.linearDamping = value
            }

        override var angularDamping: Real
            get() = super.angularDamping
            set(value) {
                body.angularDamping = value
            }

        override fun sleep() {
            body.sleep()
        }

        override fun wakeUp(strong: Boolean) {
            body.wakeUp(strong)
        }

        override fun applyForce(force: Vec) {
            pushArena { arena ->
                body.addForce(force.toVector(arena), false)
            }
        }

        override fun applyForceAt(force: Vec, at: Vec) {
            pushArena { arena ->
                body.addForceAtPoint(force.toVector(arena), at.toVector(arena), false)
            }
        }

        override fun applyImpulse(impulse: Vec) {
            pushArena { arena ->
                body.applyImpulse(impulse.toVector(arena), false)
            }
        }

        override fun applyImpulseAt(impulse: Vec, at: Vec) {
            pushArena { arena ->
                body.applyImpulseAtPoint(impulse.toVector(arena), at.toVector(arena), false)
            }
        }

        override fun applyTorque(torque: Vec) {
            pushArena { arena ->
                body.addTorque(torque.toAngVector(arena), false)
            }
        }

        override fun applyTorqueImpulse(torqueImpulse: Vec) {
            pushArena { arena ->
                body.applyTorqueImpulse(torqueImpulse.toAngVector(arena), false)
            }
        }

        override fun kinematicTarget(position: Iso) {
            pushArena { arena ->
                body.setNextKinematicPosition(position.toIsometry(arena))
            }
        }
    }
}
