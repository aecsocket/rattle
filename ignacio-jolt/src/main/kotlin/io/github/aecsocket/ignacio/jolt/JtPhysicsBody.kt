package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.core.PhysicsBody
import io.github.aecsocket.ignacio.core.FluidSettings
import io.github.aecsocket.ignacio.core.ObjectLayer
import io.github.aecsocket.ignacio.core.Shape
import io.github.aecsocket.ignacio.core.math.*
import jolt.physics.Activation
import jolt.physics.PhysicsSystem
import jolt.physics.body.Body
import jolt.physics.body.BodyLockInterface
import jolt.physics.body.BodyLockRead
import jolt.physics.body.BodyLockWrite
import jolt.physics.body.MutableBody
import java.util.Objects
import java.util.function.Consumer

data class JtObjectLayer(val layer: JObjectLayer) : ObjectLayer {
    override fun toString() = "${layer.id}"
}

class JtPhysicsBody(
    val physics: PhysicsSystem,
    val id: BodyId,
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
                AABB(min.d() + translation, max.d() + translation)
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

    override val isValid: Boolean
        get() = !physics.isDestroyed && physics.bodyInterfaceNoLock.isAdded(id.id)


    fun readAccess(body: Body): PhysicsBody.Read = when {
        body.isStatic -> StaticRead(body)
        else -> MovingRead(body)
    }

    fun writeAccess(body: MutableBody): PhysicsBody.Write = when {
        body.isStatic -> StaticWrite(body)
        else -> MovingWrite(body)
    }

    private inline fun readWith(lockInterface: BodyLockInterface, crossinline block: (PhysicsBody.Read) -> Unit): Boolean = useMemory {
        val bodyLock = BodyLockRead.of(this)
        lockInterface.lockRead(id.id, bodyLock)
        val result = bodyLock.body?.let { body ->
            block(readAccess(body))
            true
        } ?: false
        physics.bodyLockInterface.unlockRead(bodyLock)
        result
    }

    override fun read(block: Consumer<PhysicsBody.Read>) = readWith(physics.bodyLockInterface) { block.accept(it) }

    override fun readUnlocked(block: Consumer<PhysicsBody.Read>) = readWith(physics.bodyLockInterfaceNoLock) { block.accept(it) }

    private inline fun writeWith(lockInterface: BodyLockInterface, crossinline block: (PhysicsBody.Write) -> Unit): Boolean = useMemory {
        val bodyLock = BodyLockWrite.of(this)
        lockInterface.lockWrite(id.id, bodyLock)
        val result = bodyLock.body?.let { body ->
            block(writeAccess(body))
            true
        } ?: false
        physics.bodyLockInterface.unlockWrite(bodyLock)
        result
    }

    override fun write(block: Consumer<PhysicsBody.Write>) = writeWith(physics.bodyLockInterface) { block.accept(it) }

    override fun writeUnlocked(block: Consumer<PhysicsBody.Write>) = writeWith(physics.bodyLockInterfaceNoLock) { block.accept(it) }

    override fun toString(): String = id.toString()

    override fun equals(other: Any?) = other is JtPhysicsBody
            && physics == other.physics
            && id == other.id

    override fun hashCode(): Int {
        return Objects.hash(physics, id)
    }
}
