package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.rattle.*
import rapier.data.ArenaKey

@JvmInline
value class RapierRigidBodyKey(val id: Long) : RigidBodyKey {
    override fun toString(): String = ArenaKey.asString(id)
}

sealed class RapierRigidBody(
    override val handle: rapier.dynamics.RigidBody,
    override var space: RapierSpace?,
) : RapierNative(), RapierPhysicsNative, RigidBody {
    override val type: RigidBodyType
        get() = handle.bodyType.toRattle()

    override val colliders: Collection<RapierColliderKey>
        get() = handle.colliders.map { RapierColliderKey(it) }

    override val position: Iso
        get() = handle.position.toIso()

    override val linearVelocity: Vec
        get() = handle.linearVelocity.toVec()

    override val angularVelocity: Vec
        get() = handle.angularVelocity.toVec()

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
        get() = handle.userForce.toVec()

    override val appliedTorque: Vec
        get() = handle.userTorque.toVec()

    override fun kineticEnergy(): Real {
        return handle.kineticEnergy
    }

    class Read internal constructor(
        handle: rapier.dynamics.RigidBody,
        space: RapierSpace?,
    ) : RapierRigidBody(handle, space) {
        override val nativeType get() = "RapierRigidBody.Read"
    }

    class Write internal constructor(
        override val handle: rapier.dynamics.RigidBody.Mut,
        space: RapierSpace?,
    ) : RapierRigidBody(handle, space), RigidBody.Own {
        override val nativeType get() = "RapierRigidBody.Write"

        private val destroyed = DestroyFlag()

        override fun destroy() {
            destroyed()
            space?.let { space ->
                throw IllegalStateException("Attempting to remove $this while still attached to $space")
            }
            handle.drop()
        }

        override fun type(value: RigidBodyType): Write {
            handle.setBodyType(value.toRapier(), false)
            return this
        }

        override fun position(value: Iso): Write {
            handle.setPosition(value.toIsometry(), false)
            return this
        }

        override fun linearVelocity(value: Vec): Write {
            handle.setLinearVelocity(value.toVector(), false)
            return this
        }

        override fun angularVelocity(value: Vec): Write {
            handle.setAngularVelocity(value.toAngVector(), false)
            return this
        }

        override fun isCcdEnabled(value: Boolean): Write {
            handle.enableCcd(value)
            return this
        }

        override fun gravityScale(value: Real): Write {
            handle.setGravityScale(value, false)
            return this
        }

        override fun linearDamping(value: Real): Write {
            handle.linearDamping = value
            return this
        }

        override fun angularDamping(value: Real): Write {
            handle.angularDamping = value
            return this
        }

        override fun canSleep(value: Boolean): Write {
            // values taken from rigid_body_components.rs > impl RigidBodyActivation
            when (value) {
                false -> {
                    handle.activation.linearThreshold = -1.0
                    handle.activation.angularThreshold = -1.0
                }
                true -> {
                    handle.activation.linearThreshold = 0.4
                    handle.activation.angularThreshold = 0.5
                }
            }
            return this
        }

        override fun sleep() {
            handle.sleep()
        }

        override fun wakeUp(strong: Boolean) {
            handle.wakeUp(strong)
        }

        override fun applyForce(force: Vec) {
            handle.addForce(force.toVector(), false)
        }

        override fun applyForceAt(force: Vec, at: Vec) {
            handle.addForceAtPoint(force.toVector(), at.toVector(), false)
        }

        override fun applyImpulse(impulse: Vec) {
            handle.applyImpulse(impulse.toVector(), false)
        }

        override fun applyImpulseAt(impulse: Vec, at: Vec) {
            handle.applyImpulseAtPoint(impulse.toVector(), at.toVector(), false)
        }

        override fun applyTorque(torque: Vec) {
            handle.addTorque(torque.toAngVector(), false)
        }

        override fun applyTorqueImpulse(torqueImpulse: Vec) {
            handle.applyTorqueImpulse(torqueImpulse.toAngVector(), false)
        }

        override fun moveTo(to: Iso) {
            handle.setNextKinematicPosition(to.toIsometry())
        }
    }
}
