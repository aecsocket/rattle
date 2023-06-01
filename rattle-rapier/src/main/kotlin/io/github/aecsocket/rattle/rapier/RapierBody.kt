package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.rattle.*
import rapier.data.ArenaKey

@JvmInline
value class RigidBodyHandle(val id: Long) {
    override fun toString(): String = ArenaKey.asString(id)
}

class RapierBody internal constructor(
    var state: State,
) : RigidBody {
    sealed interface State {
        data class Removed(
            val body: rapier.dynamics.RigidBody.Mut,
        ) : State

        data class Added(
            val space: RapierSpace,
            val handle: RigidBodyHandle,
        ) : State
    }

    private val destroyed = DestroyFlag()

    override fun destroy() {
        when (val state = state) {
            is State.Removed -> {
                destroyed()
                state.body.drop()
            }
            is State.Added -> throw IllegalStateException("$this is still added to ${state.space}")
        }
    }

    override fun <R> read(block: (RigidBody.Read) -> R): R {
        return when (val state = state) {
            is State.Removed -> block(Read(state.body))
            is State.Added -> block(Read(state.space.rigidBodySet.get(state.handle.id)
                ?: throw IllegalArgumentException("No body with ID ${state.handle}")))
        }
    }

    override fun <R> write(block: (RigidBody.Write) -> R): R {
        return when (val state = state) {
            is State.Removed -> block(Write(state.body))
            is State.Added -> block(Write(state.space.rigidBodySet.getMut(state.handle.id)
                ?: throw IllegalArgumentException("No body with ID ${state.handle}")))
        }
    }

    override fun toString() = when (val state = state) {
        is State.Added -> "RapierBody[${state.handle}]"
        is State.Removed -> "RapierBody[0x%x]".format(state.body.addr())
    }

    override fun equals(other: Any?) = other is RapierBody && state == other.state

    override fun hashCode() = state.hashCode()

    open inner class Access(
        internal open val body: rapier.dynamics.RigidBody,
    ) : RigidBody.Access {
        override val handle get() = this@RapierBody

        override val type: RigidBodyType
            get() = body.bodyType.convert()

        override val colliders: Collection<Collider>
            get() = when (val state = state) {
                is State.Added -> body.colliders.map {
                    RapierCollider(RapierCollider.State.Added(
                        space = state.space,
                        handle = ColliderHandle(it),
                    ))
                }
                is State.Removed -> emptyList()
            }

        override val position: Iso
            get() = pushArena { arena ->
                body.getPosition(arena).toIso()
            }

        override val linearVelocity: Vec
            get() = pushArena { arena ->
                body.getLinearVelocity(arena).toVec()
            }

        override val angularVelocity: Vec
            get() = pushArena { arena ->
                body.getAngularVelocity(arena).toVec()
            }

        override val isCcdEnabled: Boolean
            get() = body.isCcdEnabled

        override val isCcdActive: Boolean
            get() = body.isCcdActive

        override val gravityScale: Real
            get() = body.gravityScale

        override val linearDamping: Real
            get() = body.linearDamping

        override val angularDamping: Real
            get() = body.angularDamping

        override val isSleeping: Boolean
            get() = body.isSleeping

        override val appliedForce: Vec
            get() = pushArena { arena ->
                body.getUserForce(arena).toVec()
            }

        override val appliedTorque: Vec
            get() = pushArena { arena ->
                body.getUserTorque(arena).toVec()
            }

        override fun kineticEnergy(): Real {
            return body.kineticEnergy
        }
    }

    inner class Read(
        body: rapier.dynamics.RigidBody,
    ) : Access(body), RigidBody.Read

    inner class Write(
        override val body: rapier.dynamics.RigidBody.Mut,
    ) : Access(body), RigidBody.Write {
        override var position: Iso
            get() = super.position
            set(value) = pushArena { arena ->
                body.setPosition(value.toIsometry(arena), false)
            }

        override var type: RigidBodyType
            get() = super.type
            set(value) {
                val oldType = body.bodyType
                val newType = value.convert()
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

        override fun kinematicMoveTo(to: Iso) {
            pushArena { arena ->
                body.setNextKinematicPosition(to.toIsometry(arena))
            }
        }
    }
}
