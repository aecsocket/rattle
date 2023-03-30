package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.*
import io.github.aecsocket.klam.DVec3
import jolt.physics.Activation
import jolt.physics.PhysicsSystem
import jolt.physics.body.*
import java.util.Objects
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

private fun assertAdded(body: Body) {
    if (!body.isInBroadPhase)
        throw IllegalStateException("Body is not added to physics space")
}

data class JtPhysicsBody internal constructor(
    val physics: PhysicsSystem,
    val name: String?,
    val id: Int,
) : PhysicsBody {
    val destroyed = AtomicBoolean(false)
    override val added get() = physics.bodyInterface.isAdded(id)

    private interface Access : PhysicsBody.Access {
        override val key: JtPhysicsBody
        val body: Body

        override val active get() = body.isActive

        override val objectLayer get() = JtObjectLayer(body.objectLayer)

        override val position get() = pushMemory { arena ->
            arena.JtDVec3().also { body.getPosition(it) }.asIgnacio()
        }

        override val rotation get() = pushMemory { arena ->
            arena.JtQuat().also { body.getRotation(it) }.asIgnacio()
        }

        override val transform get() = pushMemory { arena ->
            val position = arena.JtDVec3().also { body.getPosition(it) }.asIgnacio()
            val rotation = arena.JtQuat().also { body.getRotation(it) }.asIgnacio()
            Transform(position, rotation)
        }

        override val shape get(): Shape = JtShape(body.shape)

        override val trigger get() = body.isSensor
    }

    private interface Write : Access, PhysicsBody.Write {
        override val body: MutableBody

        override var position: DVec3
            get() = super.position
            set(value) = pushMemory { arena ->
                key.physics.bodyInterfaceNoLock.setPosition(key.id, arena.asJolt(value), Activation.DONT_ACTIVATE)
            }

        override var rotation: Quat
            get() = super.rotation
            set(value) = pushMemory { arena ->
                key.physics.bodyInterfaceNoLock.setRotation(key.id, arena.asJolt(value), Activation.DONT_ACTIVATE)
            }

        override var transform: Transform
            get() = super.transform
            set(value) = pushMemory { arena ->
                key.physics.bodyInterfaceNoLock.setPositionAndRotation(
                    key.id,
                    arena.asJolt(value.position),
                    arena.asJolt(value.rotation),
                    Activation.DONT_ACTIVATE
                )
            }

        override var shape: Shape
            get() = super.shape
            set(value) {
                value as JtShape
                // TODO update mass properties?
                key.physics.bodyInterfaceNoLock.setShape(key.id, value.handle, false, Activation.DONT_ACTIVATE)
            }

        override var trigger: Boolean
            get() = super.trigger
            set(value) { body.setIsSensor(value) }
    }

    private interface StaticAccess : Access, PhysicsBody.StaticAccess {
        override fun asDescriptor() = StaticBodyDescriptor(
            name = key.name,
            shape = shape,
            objectLayer = objectLayer,
            trigger = trigger,
        )
    }

    private interface StaticWrite : StaticAccess, Write, PhysicsBody.StaticWrite

    private interface MovingAccess : Access, PhysicsBody.MovingAccess {
        override val kinematic: Boolean
            get() = body.isKinematic

        override val linearVelocity: Vec3
            get() = pushMemory { arena ->
                arena.JtFVec3().also { key.physics.bodyInterfaceNoLock.getLinearVelocity(key.id, it) }.asIgnacio()
            }

        override val angularVelocity: Vec3
            get() = pushMemory { arena ->
                arena.JtFVec3().also { key.physics.bodyInterfaceNoLock.getAngularVelocity(key.id, it) }.asIgnacio()
            }

        override val gravityFactor: Float
            get() = body.motionProperties.gravityFactor

        override fun asDescriptor() = MovingBodyDescriptor(
            name = key.name,
            shape = shape,
            objectLayer = objectLayer,
            trigger = trigger,
            kinematic = kinematic,
            linearVelocity = linearVelocity,
            angularVelocity = angularVelocity,
            gravityFactor = gravityFactor,
        )
    }

    private interface MovingWrite : MovingAccess, Write, PhysicsBody.MovingWrite {
        override var kinematic: Boolean
            get() = super.kinematic
            set(value) { body.motionType = if (value) MotionType.KINEMATIC else MotionType.DYNAMIC }

        override var linearVelocity: Vec3
            get() = super.linearVelocity
            set(value) = pushMemory { arena ->
                body.setLinearVelocityClamped(arena.asJolt(value))
            }

        override var angularVelocity: Vec3
            get() = super.angularVelocity
            set(value) = pushMemory { arena ->
                body.setAngularVelocityClamped(arena.asJolt(value))
            }

        override var gravityFactor: Float
            get() = super.gravityFactor
            set(value) { body.motionProperties.gravityFactor = value }

        override fun activate() {
            assertAdded(body)
            key.physics.bodyInterfaceNoLock.activateBody(key.id)
        }

        override fun deactivate() {
            assertAdded(body)
            key.physics.bodyInterfaceNoLock.deactivateBody(key.id)
        }

        override fun applyForce(force: Vec3) = pushMemory { arena ->
            body.addForce(arena.asJolt(force))
        }

        override fun applyForceAt(force: Vec3, at: RVec3) = pushMemory { arena ->
            body.addForce(arena.asJolt(force), arena.asJolt(at))
        }

        override fun applyImpulse(impulse: Vec3) = pushMemory { arena ->
            body.addImpulse(arena.asJolt(impulse))
        }

        override fun applyImpulseAt(impulse: Vec3, at: RVec3) = pushMemory { arena ->
            body.addImpulse(arena.asJolt(impulse), arena.asJolt(at))
        }

        override fun applyTorque(torque: Vec3) = pushMemory { arena ->
            body.addTorque(arena.asJolt(torque))
        }

        override fun applyAngularImpulse(impulse: Vec3) = pushMemory { arena ->
            body.addAngularImpulse(arena.asJolt(impulse))
        }
    }

    private inner class StaticReadImpl(override val body: Body) : StaticAccess, PhysicsBody.StaticRead {
        override val key get() = this@JtPhysicsBody
    }

    private inner class StaticWriteImpl(override val body: MutableBody) : StaticWrite {
        override val key get() = this@JtPhysicsBody
    }

    private inner class MovingReadImpl(override val body: Body) : MovingAccess, PhysicsBody.MovingRead {
        override val key get() = this@JtPhysicsBody
    }

    private inner class MovingWriteImpl(override val body: MutableBody) : MovingWrite {
        override val key get() = this@JtPhysicsBody
    }

    fun readAccess(body: Body): PhysicsBody.Read = when {
        body.isStatic -> StaticReadImpl(body)
        else -> MovingReadImpl(body)
    }

    fun writeAccess(body: MutableBody): PhysicsBody.Write = when {
        body.isStatic -> StaticWriteImpl(body)
        else -> MovingWriteImpl(body)
    }

    private inline fun readWith(
        locker: BodyLockInterface,
        crossinline block: (PhysicsBody.Read) -> Unit
    ): Boolean = pushMemory { arena ->
        val lock = BodyLockRead.of(arena)
        locker.lockRead(id, lock)
        var success = false
        try {
            lock.body?.let { body ->
                block(readAccess(body))
                success = true
            }
        } finally {
            locker.unlockRead(lock)
        }
        success
    }

    private inline fun writeWith(
        locker: BodyLockInterface,
        crossinline block: (PhysicsBody.Write) -> Unit
    ): Boolean = pushMemory { arena ->
        val lock = BodyLockWrite.of(arena)
        locker.lockWrite(id, lock)
        var success = false
        try {
            lock.body?.let { body ->
                block(writeAccess(body))
                success = true
            }
        } finally {
            locker.unlockWrite(lock)
        }
        success
    }

    override fun read(block: Consumer<PhysicsBody.Read>) = readWith(physics.bodyLockInterface, block::accept)

    override fun readUnlocked(block: Consumer<PhysicsBody.Read>) = readWith(physics.bodyLockInterfaceNoLock, block::accept)

    override fun write(block: Consumer<PhysicsBody.Write>) = writeWith(physics.bodyLockInterface, block::accept)

    override fun writeUnlocked(block: Consumer<PhysicsBody.Write>) = writeWith(physics.bodyLockInterfaceNoLock, block::accept)

    override fun toString(): String = name?.let { name ->
        "$name (${BodyIds.asString(id)})"
    } ?: BodyIds.asString(id)

    override fun equals(other: Any?) = other is JtPhysicsBody
            && physics == other.physics
            && id == other.id

    override fun hashCode() = Objects.hash(physics, id)
}
