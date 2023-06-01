package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.rattle.*
import rapier.data.ArenaKey

data class RapierRigidBodyHandle(val id: Long) : RigidBodyHandle {
    override fun toString(): String = ArenaKey.asString(id)
}

object RapierRigidBody {
    sealed class Base(
        override val handle: rapier.dynamics.RigidBody,
        var space: RapierSpace? = null,
    ) : RapierNative(), RigidBody.Read {
        override val type: RigidBodyType
            get() = handle.bodyType.convert()

        override val colliders: Collection<RapierColliderHandle>
            get() = handle.colliders.map { RapierColliderHandle(it) }

        override val position: Iso
            get() = pushArena { arena ->
                handle.getPosition(arena).toIso()
            }

        override val linearVelocity: Vec
            get() = pushArena { arena ->
                handle.getLinearVelocity(arena).toVec()
            }

        override val angularVelocity: Vec
            get() = pushArena { arena ->
                handle.getAngularVelocity(arena).toVec()
            }

        override val isCcdEnabled: Boolean
            get() = handle.isCcdEnabled

        override val isCcdActive: Boolean
            get() = handle.isCcdActive

        override val gravityScale: Real
            get() = handle.gravityScale

        override val linearDamping: Real
            get() = handle.linearDamping

        override val angularDamping: Real
            get() = handle.angularDamping

        override val isSleeping: Boolean
            get() = handle.isSleeping

        override val appliedForce: Vec
            get() = pushArena { arena ->
                handle.getUserForce(arena).toVec()
            }

        override val appliedTorque: Vec
            get() = pushArena { arena ->
                handle.getUserTorque(arena).toVec()
            }

        override fun kineticEnergy(): Real {
            return handle.kineticEnergy
        }
    }

    class Read internal constructor(
        handle: rapier.dynamics.RigidBody,
        space: RapierSpace? = null,
    ) : Base(handle, space) {
        override val nativeType get() = "RapierRigidBody.Read"
    }

    open class Write internal constructor(
        override val handle: rapier.dynamics.RigidBody.Mut,
        space: RapierSpace? = null,
    ) : Base(handle, space), RigidBody.Write {
        override val nativeType get() = "RapierRigidBody.Write"

        override var position: Iso
            get() = super.position
            set(value) = pushArena { arena ->
                handle.setPosition(value.toIsometry(arena), false)
            }

        override var type: RigidBodyType
            get() = super.type
            set(value) {
                val oldType = handle.bodyType
                val newType = value.convert()
                if (oldType == newType) return
                handle.setBodyType(newType, false)
            }

        override var isCcdEnabled: Boolean
            get() = super.isCcdEnabled
            set(value) {
                handle.enableCcd(value)
            }

        override var linearVelocity: Vec
            get() = super.linearVelocity
            set(value) = pushArena { arena ->
                handle.setLinearVelocity(value.toVector(arena), false)
            }

        override var angularVelocity: Vec
            get() = super.angularVelocity
            set(value) = pushArena { arena ->
                handle.setAngularVelocity(value.toAngVector(arena), false)
            }

        override var gravityScale: Real
            get() = super.gravityScale
            set(value) {
                handle.setGravityScale(value, false)
            }

        override var linearDamping: Real
            get() = super.linearDamping
            set(value) {
                handle.linearDamping = value
            }

        override var angularDamping: Real
            get() = super.angularDamping
            set(value) {
                handle.angularDamping = value
            }

        override fun sleep() {
            handle.sleep()
        }

        override fun wakeUp(strong: Boolean) {
            handle.wakeUp(strong)
        }

        override fun applyForce(force: Vec) {
            pushArena { arena ->
                handle.addForce(force.toVector(arena), false)
            }
        }

        override fun applyForceAt(force: Vec, at: Vec) {
            pushArena { arena ->
                handle.addForceAtPoint(force.toVector(arena), at.toVector(arena), false)
            }
        }

        override fun applyImpulse(impulse: Vec) {
            pushArena { arena ->
                handle.applyImpulse(impulse.toVector(arena), false)
            }
        }

        override fun applyImpulseAt(impulse: Vec, at: Vec) {
            pushArena { arena ->
                handle.applyImpulseAtPoint(impulse.toVector(arena), at.toVector(arena), false)
            }
        }

        override fun applyTorque(torque: Vec) {
            pushArena { arena ->
                handle.addTorque(torque.toAngVector(arena), false)
            }
        }

        override fun applyTorqueImpulse(torqueImpulse: Vec) {
            pushArena { arena ->
                handle.applyTorqueImpulse(torqueImpulse.toAngVector(arena), false)
            }
        }

        override fun kinematicMoveTo(to: Iso) {
            pushArena { arena ->
                handle.setNextKinematicPosition(to.toIsometry(arena))
            }
        }
    }

    class Own internal constructor(
        handle: rapier.dynamics.RigidBody.Mut,
        space: RapierSpace? = null,
    ) : Write(handle, space), RigidBody.Own {
        override val nativeType get() = "RapierRigidBody.Own"

        private val destroyed = DestroyFlag()

        override fun destroy() {
            destroyed()
            space?.let { space ->
                throw IllegalStateException("Attempting to remove $this while still attached to $space")
            }
            handle.drop()
        }
    }
}
