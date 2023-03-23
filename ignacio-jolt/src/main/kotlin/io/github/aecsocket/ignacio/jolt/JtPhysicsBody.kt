package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.alexandria.core.math.*
import io.github.aecsocket.ignacio.core.*
import jolt.physics.Activation
import jolt.physics.PhysicsSystem
import jolt.physics.body.*
import java.util.*
import java.util.function.Consumer

private const val INVALID_BODY_ID = -0x1 // 0xffffffff
private const val BROAD_PHASE_BIT = 0x00800000

class JtPhysicsBody(
    val physics: PhysicsSystem,
    val id: BodyId,
    override val name: String?,
    override var added: Boolean,
) : PhysicsBody {
    // base classes
    private interface Access : PhysicsBody.Access {
        val handle: Body

        override val isActive: Boolean
            get() = handle.isActive

        override val objectLayer: ObjectLayer
            get() = JtObjectLayer(JObjectLayer(handle.objectLayer))

        override val position: Vec3d
            get() = useMemory {
                DVec3().also { handle.getPosition(it) }.toIgnacio()
            }

        override val rotation: Quat
            get() = useMemory {
                JQuat().also { handle.getRotation(it) }.toIgnacio()
            }

        override val transform: Transform
            get() = useMemory {
                val position = DVec3()
                val rotation = JQuat()
                handle.getPosition(position)
                handle.getRotation(rotation)
                Transform(position.toIgnacio(), rotation.toIgnacio())
            }

        override val boundingBox: AABB
            get() = useMemory {
                val comTransform = DMat44()
                handle.getCenterOfMassTransform(comTransform)
                val translation = Vec3d(
                    comTransform.getTranslation(0),
                    comTransform.getTranslation(1),
                    comTransform.getTranslation(2)
                )
                val fComTransform = FMat44()
                fComTransform.read(comTransform.rotationComponents(), floatArrayOf(0.0f, 0.0f, 0.0f))
                val out = AABox()
                handle.shape.getWorldSpaceBounds(fComTransform, Vec3f.One.toJolt(), out)
                val min = out.min.toIgnacio()
                val max = out.max.toIgnacio()
                AABB(Vec3d(min) + translation, Vec3d(max) + translation)
            }

        override val shape: Shape
            get() = JtShape(handle.shape)
    }

    private interface MovingAccess : Access, PhysicsBody.MovingAccess {
        override val linearVelocity: Vec3f
            get() = useMemory {
                FVec3().also { handle.getLinearVelocity(it) }.toIgnacio()
            }

        override val angularVelocity: Vec3f
            get() = useMemory {
                FVec3().also { handle.getAngularVelocity(it) }.toIgnacio()
            }
    }

    private interface Write : Access, PhysicsBody.Write {
        override val handle: MutableBody

        val bodyId: Int

        val physics: PhysicsSystem

        override val isActive: Boolean
            get() = super.isActive

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

        override var shape: Shape
            get() = super.shape
            set(value) {
                value as JtShape
                // TODO update mass props?
                physics.bodyInterfaceNoLock.setShape(bodyId, value.handle, false, Activation.DONT_ACTIVATE)
            }
    }

    // impls
    private open inner class BaseAccess(override val handle: Body) : Access {
        override val body: PhysicsBody
            get() = this@JtPhysicsBody
    }

    private inner class StaticRead(body: Body) : BaseAccess(body), PhysicsBody.StaticRead

    private inner class MovingRead(body: Body) : BaseAccess(body), MovingAccess, PhysicsBody.MovingRead

    private open inner class BaseWrite(override val handle: MutableBody) : BaseAccess(handle), Write {
        override val bodyId: Int
            get() = this@JtPhysicsBody.id.id

        override val physics: PhysicsSystem
            get() = this@JtPhysicsBody.physics
    }

    private inner class StaticWrite(override val handle: MutableBody) : BaseWrite(handle), PhysicsBody.StaticWrite

    private inner class MovingWrite(body: MutableBody) : BaseWrite(body), MovingAccess, PhysicsBody.MovingWrite {
        override var linearVelocity: Vec3f
            get() = super.linearVelocity
            set(value) = useMemory {
                handle.setLinearVelocityClamped(value.toJolt())
            }

        override var angularVelocity: Vec3f
            get() = super.angularVelocity
            set(value) = useMemory {
                handle.setAngularVelocityClamped(value.toJolt())
            }

        override fun activate() {
            physics.bodyInterfaceNoLock.activateBody(bodyId)
        }

        override fun deactivate() {
            physics.bodyInterfaceNoLock.deactivateBody(bodyId)
        }

        override fun applyForce(force: Vec3f): Unit = useMemory {
            handle.addForce(force.toJolt())
        }

        override fun applyForceAt(force: Vec3f, at: Vec3d): Unit = useMemory {
            handle.addForce(force.toJolt(), at.toJolt())
        }

        override fun applyImpulse(impulse: Vec3f): Unit = useMemory {
            handle.addImpulse(impulse.toJolt())
        }

        override fun applyImpulseAt(impulse: Vec3f, at: Vec3d): Unit = useMemory {
            handle.addImpulse(impulse.toJolt(), at.toJolt())
        }

        override fun applyTorque(torque: Vec3f): Unit = useMemory {
            handle.addTorque(torque.toJolt())
        }

        override fun applyAngularImpulse(impulse: Vec3f): Unit = useMemory {
            handle.addAngularImpulse(impulse.toJolt())
        }

        override fun applyBuoyancy(
            deltaTime: Float,
            buoyancy: Float,
            fluidSurface: Vec3d,
            fluidNormal: Vec3f,
            fluidVelocity: Vec3f,
            fluid: FluidSettings
        ): Unit = useMemory {
            val gravity = FVec3().also { physics.getGravity(it) }
            handle.applyBuoyancyImpulse(
                fluidSurface.toJolt(),
                fluidNormal.toJolt(),
                buoyancy,
                fluid.linearDrag,
                fluid.angularDrag,
                fluidVelocity.toJolt(),
                gravity,
                deltaTime,
            )
        }
    }

    internal var isDestroyed = false

    override val valid: Boolean
        get() = !physics.isDestroyed && !isDestroyed

    fun assertCanBeDestroyed() {
        if (isDestroyed)
            throw IllegalStateException("Already destroyed")
    }

    fun assertCanBeAdded() {
        if (added)
            throw IllegalStateException("Already added")
        if (isDestroyed)
            throw IllegalStateException("Already destroyed")
    }

    fun assertCanBeRemoved() {
        if (!added)
            throw IllegalStateException("Already removed")
        if (isDestroyed)
            throw IllegalStateException("Already destroyed")
    }

    fun readAccess(body: Body): PhysicsBody.Read = when {
        body.isStatic -> StaticRead(body)
        else -> MovingRead(body)
    }

    fun writeAccess(body: MutableBody): PhysicsBody.Write = when {
        body.isStatic -> StaticWrite(body)
        else -> MovingWrite(body)
    }

    private fun assertCanLock() {
        if (id.id and BROAD_PHASE_BIT != 0)
            throw IllegalStateException("Body is in broad phase (already unlocked)")
        if (id.id == INVALID_BODY_ID)
            throw IllegalStateException("Body ID is invalid")
    }

    private inline fun readWith(locking: Boolean, lockInterface: BodyLockInterface, crossinline block: (PhysicsBody.Read) -> Unit): Boolean = useMemory {
        if (locking)
            assertCanLock()
        val bodyLock = BodyLockRead.of(this)
        lockInterface.lockRead(id.id, bodyLock)
        val result = bodyLock.body?.let { body ->
            block(readAccess(body))
            true
        } ?: false
        lockInterface.unlockRead(bodyLock)
        result
    }

    override fun read(block: Consumer<PhysicsBody.Read>) = readWith(true, physics.bodyLockInterface) { block.accept(it) }

    override fun readUnlocked(block: Consumer<PhysicsBody.Read>) = readWith(false, physics.bodyLockInterfaceNoLock) { block.accept(it) }

    private inline fun writeWith(locking: Boolean, lockInterface: BodyLockInterface, crossinline block: (PhysicsBody.Write) -> Unit): Boolean = useMemory {
        if (locking)
            assertCanLock()
        val bodyLock = BodyLockWrite.of(this)
        lockInterface.lockWrite(id.id, bodyLock)
        val result = bodyLock.body?.let { body ->
            block(writeAccess(body))
            true
        } ?: false
        lockInterface.unlockWrite(bodyLock)
        result
    }

    override fun write(block: Consumer<PhysicsBody.Write>) = writeWith(true, physics.bodyLockInterface) { block.accept(it) }

    override fun writeUnlocked(block: Consumer<PhysicsBody.Write>) = writeWith(false, physics.bodyLockInterfaceNoLock) { block.accept(it) }

    override fun toString(): String = name?.let { "$name ($id)" } ?: id.toString()

    override fun equals(other: Any?) = other is JtPhysicsBody
            && physics == other.physics
            && id == other.id

    override fun hashCode(): Int {
        return Objects.hash(physics, id)
    }
}
