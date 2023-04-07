package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.*
import io.github.aecsocket.klam.DAabb3
import io.github.aecsocket.klam.DVec3
import io.github.aecsocket.klam.FVec3
import io.github.aecsocket.klam.plus
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
    val engine: JoltEngine,
    val physics: PhysicsSystem,
    val id: Int,
) : PhysicsBody {
    val destroyed = AtomicBoolean(false)
    override val isAdded get() = physics.bodyInterface.isAdded(id)

    private interface Access : PhysicsBody.Access {
        override val key: JtPhysicsBody
        val body: Body

        override val isActive get() = body.isActive

        override val contactFilter get() = key.engine.JtBodyContactFilter(body.objectLayer)

        override val position get() = pushArena { arena ->
            arena.DVec3().also { body.getPosition(it) }.asIgnacio()
        }

        override val rotation get() = pushArena { arena ->
            arena.Quat().also { body.getRotation(it) }.asIgnacio()
        }

        override val transform get() = pushArena { arena ->
            val position = arena.DVec3().also { body.getPosition(it) }.asIgnacio()
            val rotation = arena.Quat().also { body.getRotation(it) }.asIgnacio()
            Transform(position, rotation)
        }

        override val bounds get() = pushArena { arena ->
            val comTransform = arena.DMat44().also { body.getCenterOfMassTransform(it) }
            val translation = DVec3(
                comTransform.getTranslation(0),
                comTransform.getTranslation(1),
                comTransform.getTranslation(2),
            )
            val fComTransform = arena.FMat44()
            fComTransform.read(comTransform.rotationComponents(), floatArrayOf(0.0f, 0.0f, 0.0f))

            val out = arena.AABox().also { body.shape.getWorldSpaceBounds(fComTransform, arena.asJolt(FVec3(1.0f)), it) }
            DAabb3(DVec3(out.min.asIgnacio()) + translation, DVec3(out.max.asIgnacio()) + translation)
        }

        override val shape get(): Shape = JtShape(body.shape)

        override val isTrigger get() = body.isSensor
    }

    private interface Write : Access, PhysicsBody.Write {
        override val body: MutableBody

        override var position: DVec3
            get() = super.position
            set(value) = pushArena { arena ->
                key.physics.bodyInterfaceNoLock.setPosition(key.id, arena.asJolt(value), Activation.DONT_ACTIVATE)
            }

        override var rotation: Quat
            get() = super.rotation
            set(value) = pushArena { arena ->
                key.physics.bodyInterfaceNoLock.setRotation(key.id, arena.asJolt(value), Activation.DONT_ACTIVATE)
            }

        override var transform: Transform
            get() = super.transform
            set(value) = pushArena { arena ->
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

        override var isTrigger: Boolean
            get() = super.isTrigger
            set(value) { body.setIsSensor(value) }
    }

    private interface StaticAccess : Access, PhysicsBody.StaticAccess {
        override fun asDescriptor() = StaticBodyDescriptor(
            shape = shape,
            contactFilter = contactFilter,
            isTrigger = isTrigger,
        )
    }

    private interface StaticWrite : StaticAccess, Write, PhysicsBody.StaticWrite

    private interface MovingAccess : Access, PhysicsBody.MovingAccess {
        override val isKinematic: Boolean
            get() = body.isKinematic

        override val linearVelocity: FVec3
            get() = pushArena { arena ->
                arena.FVec3().also { key.physics.bodyInterfaceNoLock.getLinearVelocity(key.id, it) }.asIgnacio()
            }

        override val angularVelocity: FVec3
            get() = pushArena { arena ->
                arena.FVec3().also { key.physics.bodyInterfaceNoLock.getAngularVelocity(key.id, it) }.asIgnacio()
            }

        override val friction: Float
            get() = body.friction

        override val restitution: Float
            get() = body.restitution

        override val gravityFactor: Float
            get() = body.motionProperties.gravityFactor

        override val linearDamping: Float
            get() = body.motionProperties.linearDamping

        override val angularDamping: Float
            get() = body.motionProperties.angularDamping

        override val maxLinearVelocity: Float
            get() = body.motionProperties.maxLinearVelocity

        override val maxAngularVelocity: Float
            get() = body.motionProperties.maxAngularVelocity

        override fun asDescriptor() = MovingBodyDescriptor(
            shape = shape,
            contactFilter = contactFilter,
            isTrigger = isTrigger,
            isKinematic = isKinematic,
            mass = Mass.Calculate,
            linearVelocity = linearVelocity,
            angularVelocity = angularVelocity,
            friction = friction,
            restitution = restitution,
            gravityFactor = gravityFactor,
            linearDamping = linearDamping,
            angularDamping = angularDamping,
            maxLinearVelocity = maxLinearVelocity,
            maxAngularVelocity = maxAngularVelocity,
        )
    }

    private interface MovingWrite : MovingAccess, Write, PhysicsBody.MovingWrite {
        override var isKinematic: Boolean
            get() = super.isKinematic
            set(value) { body.motionType = if (value) MotionType.KINEMATIC else MotionType.DYNAMIC }

        override var linearVelocity: FVec3
            get() = super.linearVelocity
            set(value) = pushArena { arena ->
                body.setLinearVelocityClamped(arena.asJolt(value))
            }

        override var angularVelocity: FVec3
            get() = super.angularVelocity
            set(value) = pushArena { arena ->
                body.setAngularVelocityClamped(arena.asJolt(value))
            }

        override var friction: Float
            get() = super.friction
            set(value) { body.friction = value }

        override var restitution: Float
            get() = super.restitution
            set(value) { body.restitution = value }

        override var gravityFactor: Float
            get() = super.gravityFactor
            set(value) { body.motionProperties.gravityFactor = value }

        override var linearDamping: Float
            get() = super.linearDamping
            set(value) { body.motionProperties.linearDamping = value }

        override var angularDamping: Float
            get() = super.angularDamping
            set(value) { body.motionProperties.angularDamping = value }

        override var maxLinearVelocity: Float
            get() = super.maxLinearVelocity
            set(value) { body.motionProperties.maxLinearVelocity = value }

        override var maxAngularVelocity: Float
            get() = super.maxAngularVelocity
            set(value) { body.motionProperties.maxAngularVelocity = value }

        override fun activate() {
            assertAdded(body)
            key.physics.bodyInterfaceNoLock.activateBody(key.id)
        }

        override fun deactivate() {
            assertAdded(body)
            key.physics.bodyInterfaceNoLock.deactivateBody(key.id)
        }

        override fun applyForce(force: FVec3) = pushArena { arena ->
            body.addForce(arena.asJolt(force))
        }

        override fun applyForceAt(force: FVec3, at: DVec3) = pushArena { arena ->
            body.addForce(arena.asJolt(force), arena.asJolt(at))
        }

        override fun applyImpulse(impulse: FVec3) = pushArena { arena ->
            body.addImpulse(arena.asJolt(impulse))
        }

        override fun applyImpulseAt(impulse: FVec3, at: DVec3) = pushArena { arena ->
            body.addImpulse(arena.asJolt(impulse), arena.asJolt(at))
        }

        override fun applyTorque(torque: FVec3) = pushArena { arena ->
            body.addTorque(arena.asJolt(torque))
        }

        override fun applyAngularImpulse(impulse: FVec3) = pushArena { arena ->
            body.addAngularImpulse(arena.asJolt(impulse))
        }

        override fun moveTo(to: Transform, deltaTime: Float) = pushArena { arena ->
            body.moveKinematic(arena.asJolt(to.position), arena.asJolt(to.rotation), deltaTime)
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
    ): Boolean = pushArena { arena ->
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
    ): Boolean = pushArena { arena ->
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

    override fun toString(): String = BodyIds.asString(id)

    override fun equals(other: Any?) = other is JtPhysicsBody
            && physics == other.physics
            && id == other.id

    override fun hashCode() = Objects.hash(physics, id)
}
