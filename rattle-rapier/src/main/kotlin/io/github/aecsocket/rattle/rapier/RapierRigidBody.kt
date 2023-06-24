package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.klam.*
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
    protected fun checkLock() = checkLock("rigid body", space?.lock)

    override val type: RigidBodyType
        get() {
            checkLock()
            return handle.bodyType.toRattle()
        }

    override val colliders: Collection<RapierColliderKey>
        get() {
            checkLock()
            return handle.colliders.map { RapierColliderKey(it) }
        }

    override val position: DIso3
        get() {
            checkLock()
            return handle.position.toIso()
        }

    override val linearVelocity: DVec3
        get() {
            checkLock()
            return handle.linearVelocity.toVec()
        }

    override val angularVelocity: DVec3
        get() {
            checkLock()
            return handle.angularVelocity.toVec()
        }

    override val isCcdEnabled: Boolean
        get() {
            checkLock()
            return handle.isCcdEnabled
        }

    override val isCcdActive: Boolean
        get() {
            checkLock()
            return handle.isCcdActive
        }

    override val gravityScale: Double
        get() {
            checkLock()
            return handle.gravityScale
        }

    override val linearDamping: Double
        get() {
            checkLock()
            return handle.linearDamping
        }

    override val angularDamping: Double
        get() {
            checkLock()
            return handle.angularDamping
        }

    override val isSleeping: Boolean
        get() {
            checkLock()
            return handle.isSleeping
        }

    override val appliedForce: DVec3
        get() {
            checkLock()
            return handle.userForce.toVec()
        }

    override val appliedTorque: DVec3
        get() {
            checkLock()
            return handle.userTorque.toVec()
        }

    override fun kineticEnergy(): Double {
        checkLock()
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
            checkLock()
            destroyed()
            space?.let { space ->
                throw IllegalStateException("Attempting to remove $this while still attached to $space")
            }
            handle.drop()
        }

        override fun type(value: RigidBodyType): Write {
            checkLock()
            handle.setBodyType(value.toRapier(), false)
            return this
        }

        override fun position(value: DIso3): Write {
            checkLock()
            handle.setPosition(value.toIsometry(), false)
            return this
        }

        override fun linearVelocity(value: DVec3): Write {
            checkLock()
            handle.setLinearVelocity(value.toVector(), false)
            return this
        }

        override fun angularVelocity(value: DVec3): Write {
            checkLock()
            handle.setAngularVelocity(value.toAngVector(), false)
            return this
        }

        override fun isCcdEnabled(value: Boolean): Write {
            checkLock()
            handle.enableCcd(value)
            return this
        }

        override fun gravityScale(value: Double): Write {
            checkLock()
            handle.setGravityScale(value, false)
            return this
        }

        override fun linearDamping(value: Double): Write {
            checkLock()
            handle.linearDamping = value
            return this
        }

        override fun angularDamping(value: Double): Write {
            checkLock()
            handle.angularDamping = value
            return this
        }

        override fun canSleep(value: Boolean): Write {
            checkLock()
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
            checkLock()
            handle.sleep()
        }

        override fun wakeUp(strong: Boolean) {
            checkLock()
            handle.wakeUp(strong)
        }

        override fun applyForce(force: DVec3) {
            checkLock()
            handle.addForce(force.toVector(), false)
        }

        override fun applyForceAt(force: DVec3, at: DVec3) {
            checkLock()
            handle.addForceAtPoint(force.toVector(), at.toVector(), false)
        }

        override fun applyImpulse(impulse: DVec3) {
            checkLock()
            handle.applyImpulse(impulse.toVector(), false)
        }

        override fun applyImpulseAt(impulse: DVec3, at: DVec3) {
            checkLock()
            handle.applyImpulseAtPoint(impulse.toVector(), at.toVector(), false)
        }

        override fun applyTorque(torque: DVec3) {
            checkLock()
            handle.addTorque(torque.toAngVector(), false)
        }

        override fun applyTorqueImpulse(torqueImpulse: DVec3) {
            checkLock()
            handle.applyTorqueImpulse(torqueImpulse.toAngVector(), false)
        }

        override fun moveTo(to: DIso3) {
            checkLock()
            handle.setNextKinematicPosition(to.toIsometry())
        }
    }
}
