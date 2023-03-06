package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.core.BodyRef
import io.github.aecsocket.ignacio.core.FluidSettings
import io.github.aecsocket.ignacio.core.ObjectLayer
import io.github.aecsocket.ignacio.core.math.*
import jolt.physics.Activation
import jolt.physics.PhysicsSystem
import jolt.physics.body.Body
import jolt.physics.body.BodyLockInterface
import jolt.physics.body.BodyLockRead
import jolt.physics.body.BodyLockWrite
import jolt.physics.body.MutableBody
import java.util.Objects

class JtObjectLayer(val layer: JObjectLayer) : ObjectLayer

class JtBodyRef(
    val physics: PhysicsSystem,
    val id: JBodyId,
) : BodyRef {
    // base classes
    private interface Access : BodyRef.Access {
        val body: Body

        override val isActive: Boolean
            get() = body.isActive

        override val position: Vec3d
            get() = useMemory {
                DVec3().also { body.getPosition(it) }.toIgnacio()
            }

        override val rotation: Quat
            get() = useMemory {
                JQuat().also { body.getRotation(it) }.toIgnacio()
            }

        override val transform: Transform
            get() = useMemory {
                val position = DVec3()
                val rotation = JQuat()
                body.getPosition(position)
                body.getRotation(rotation)
                Transform(position.toIgnacio(), rotation.toIgnacio())
            }

        override val centerOfMass: Transform
            get() = useMemory {
                DMat44().also { body.getCenterOfMassTransform(it) }.toTransform()
            }
    }

    private interface StaticAccess : Access, BodyRef.StaticAccess

    private interface MovingAccess : Access, BodyRef.MovingAccess {
        override val linearVelocity: Vec3f
            get() = useMemory {
                FVec3().also { body.getLinearVelocity(it) }.toIgnacio()
            }

        override val angularVelocity: Vec3f
            get() = useMemory {
                FVec3().also { body.getAngularVelocity(it) }.toIgnacio()
            }
    }

    private interface Write : Access, BodyRef.Write {
        override val isActive: Boolean
            get() = super.isActive

        override val body: MutableBody

        val bodyId: Int

        val physics: PhysicsSystem

        override var position: Vec3d
            get() = super.position
            set(value) = useMemory {
                physics.bodyInterfaceNoLock.setPosition(bodyId, value.toJolt(), Activation.DONT_ACTIVATE)
            }

        override var rotation: Quat
            get() = super.rotation
            set(value) = useMemory {
                physics.bodyInterfaceNoLock.setRotation(bodyId, value.toJolt(), Activation.DONT_ACTIVATE)
            }

        override var transform: Transform
            get() = super.transform
            set(value) = useMemory {
                physics.bodyInterfaceNoLock.setPositionAndRotation(bodyId, value.position.toJolt(), value.rotation.toJolt(), Activation.DONT_ACTIVATE)
            }
    }

    // impls
    private open inner class BaseAccess(override val body: Body) : Access {
        override val ref: BodyRef
            get() = this@JtBodyRef
    }

    private inner class StaticRead(body: Body) : BaseAccess(body), BodyRef.StaticRead

    private inner class MovingRead(body: Body) : BaseAccess(body), MovingAccess, BodyRef.MovingRead

    private open inner class BaseWrite(override val body: MutableBody) : BaseAccess(body), Write {
        override val bodyId: Int
            get() = this@JtBodyRef.id.id

        override val physics: PhysicsSystem
            get() = this@JtBodyRef.physics
    }

    private inner class StaticWrite(override val body: MutableBody) : BaseWrite(body), BodyRef.StaticWrite

    private inner class MovingWrite(body: MutableBody) : BaseWrite(body), MovingAccess, BodyRef.MovingWrite {
        override var linearVelocity: Vec3f
            get() = super.linearVelocity
            set(value) = useMemory {
                body.setLinearVelocityClamped(value.toJolt())
            }

        override var angularVelocity: Vec3f
            get() = super.angularVelocity
            set(value) = useMemory {
                body.setAngularVelocityClamped(value.toJolt())
            }

        override fun applyForce(force: Vec3f): Unit = useMemory {
            body.addForce(force.toJolt())
        }

        override fun applyForceAt(force: Vec3f, at: Vec3d): Unit = useMemory {
            body.addForce(force.toJolt(), at.toJolt())
        }

        override fun applyImpulse(impulse: Vec3f): Unit = useMemory {
            body.addImpulse(impulse.toJolt())
        }

        override fun applyImpulseAt(impulse: Vec3f, at: Vec3d): Unit = useMemory {
            body.addImpulse(impulse.toJolt(), at.toJolt())
        }

        override fun applyTorque(torque: Vec3f): Unit = useMemory {
            body.addTorque(torque.toJolt())
        }

        override fun applyAngularImpulse(impulse: Vec3f): Unit = useMemory {
            body.addAngularImpulse(impulse.toJolt())
        }

        override fun applyBuoyancy(deltaTime: Float, buoyancy: Float, fluid: FluidSettings): Unit = useMemory {
            val gravity = FVec3().also { physics.getGravity(it) }
            body.applyBuoyancyImpulse(
                fluid.surfacePosition.toJolt(),
                fluid.surfaceNormal.toJolt(),
                buoyancy,
                fluid.linearDrag,
                fluid.angularDrag,
                fluid.velocity.toJolt(),
                gravity,
                deltaTime,
            )
        }
    }

    override val isValid: Boolean
        get() = !physics.isDestroyed && physics.bodyInterfaceNoLock.isAdded(id.id)


    fun readAccess(body: Body): BodyRef.Read = when {
        body.isStatic -> StaticRead(body)
        else -> MovingRead(body)
    }

    fun writeAccess(body: MutableBody): BodyRef.Write = when {
        body.isStatic -> StaticWrite(body)
        else -> MovingWrite(body)
    }

    private inline fun readWith(lockInterface: BodyLockInterface, crossinline block: (BodyRef.Read) -> Unit): Boolean = useMemory {
        val bodyLock = BodyLockRead.of(this)
        lockInterface.lockRead(id.id, bodyLock)
        val result = bodyLock.body?.let { body ->
            block(readAccess(body))
            true
        } ?: false
        physics.bodyLockInterface.unlockRead(bodyLock)
        result
    }

    override fun read(block: (BodyRef.Read) -> Unit) = readWith(physics.bodyLockInterface, block)

    override fun readUnlocked(block: (BodyRef.Read) -> Unit) = readWith(physics.bodyLockInterfaceNoLock, block)

    private inline fun writeWith(lockInterface: BodyLockInterface, crossinline block: (BodyRef.Write) -> Unit): Boolean = useMemory {
        val bodyLock = BodyLockWrite.of(this)
        lockInterface.lockWrite(id.id, bodyLock)
        val result = bodyLock.body?.let { body ->
            block(writeAccess(body))
            true
        } ?: false
        physics.bodyLockInterface.unlockWrite(bodyLock)
        result
    }

    override fun write(block: (BodyRef.Write) -> Unit) = writeWith(physics.bodyLockInterface, block)

    override fun writeUnlocked(block: (BodyRef.Write) -> Unit) = writeWith(physics.bodyLockInterfaceNoLock, block)

    override fun toString(): String = id.toString()

    override fun equals(other: Any?) = other is JtBodyRef
            && physics == other.physics
            && id == other.id

    override fun hashCode(): Int {
        return Objects.hash(physics, id)
    }
}
