package io.github.aecsocket.ignacio.core

import io.github.aecsocket.ignacio.core.math.*
import java.util.function.Consumer

interface ObjectLayer

interface Shape : Destroyable

interface BodySettings {
    val shape: Shape
    val layer: ObjectLayer
    val isSensor: Boolean
}

data class StaticBodySettings(
    override val shape: Shape,
    override val layer: ObjectLayer,
    override val isSensor: Boolean = false,
) : BodySettings

data class MovingBodySettings(
    override val shape: Shape,
    override val layer: ObjectLayer,
    override val isSensor: Boolean = false,
    val mass: Float = 1.0f,
    val linearVelocity: Vec3f = Vec3f.Zero,
    val angularVelocity: Vec3f = Vec3f.Zero,
    val friction: Float = 0.2f,
    val restitution: Float = 0.0f,
    val linearDamping: Float = 0.05f,
    val angularDamping: Float = 0.05f,
    val maxLinearVelocity: Float = 500.0f,
    val maxAngularVelocity: Float = 0.25f * FPI * 60.0f,
    val gravityFactor: Float = 1.0f,
) : BodySettings

data class FluidSettings(
    val linearDrag: Float,
    val angularDrag: Float,
)

interface BodyRef {
    val isValid: Boolean

    fun read(block: Consumer<Read>): Boolean

    fun readUnlocked(block: Consumer<Read>): Boolean

    fun write(block: Consumer<Write>): Boolean

    fun writeUnlocked(block: Consumer<Write>): Boolean

    interface Access {
        val ref: BodyRef

        val isActive: Boolean

        val position: Vec3d

        val rotation: Quat

        val transform: Transform

        val centerOfMass: Transform

        val shape: Shape
    }

    interface StaticAccess : Access

    interface MovingAccess : Access {
        val linearVelocity: Vec3f

        val angularVelocity: Vec3f
    }


    interface Read : Access

    interface StaticRead : StaticAccess, Read

    interface MovingRead : MovingAccess, Read


    interface Write : Access {
        override val isActive: Boolean

        override var position: Vec3d

        override var rotation: Quat

        override var transform: Transform

        override var shape: Shape
    }

    interface StaticWrite : StaticAccess, Write

    interface MovingWrite : MovingAccess, Write {
        override var linearVelocity: Vec3f

        override var angularVelocity: Vec3f

        fun applyForce(force: Vec3f)

        fun applyForceAt(force: Vec3f, at: Vec3d)

        fun applyImpulse(impulse: Vec3f)

        fun applyImpulseAt(impulse: Vec3f, at: Vec3d)

        fun applyTorque(torque: Vec3f)

        fun applyAngularImpulse(impulse: Vec3f)

        fun applyBuoyancy(
            deltaTime: Float,
            buoyancy: Float,
            fluidSurface: Vec3d,
            fluidNormal: Vec3f,
            fluidVelocity: Vec3f,
            fluid: FluidSettings,
        )
    }
}

inline fun <reified A : BodyRef.Read> BodyRef.readOf(crossinline block: (A) -> Unit): Boolean {
    var ran = false
    read { body ->
        if (body is A) {
            block(body)
            ran = true
        }
    }
    return ran
}

inline fun <reified A : BodyRef.Read> BodyRef.readUnlockedOf(crossinline block: (A) -> Unit): Boolean {
    var ran = false
    readUnlocked { body ->
        if (body is A) {
            block(body)
            ran = true
        }
    }
    return ran
}

inline fun <reified A : BodyRef.Write> BodyRef.writeOf(crossinline block: (A) -> Unit): Boolean {
    var ran = false
    write { body ->
        if (body is A) {
            block(body)
            ran = true
        }
    }
    return ran
}

inline fun <reified A : BodyRef.Write> BodyRef.writeUnlockedOf(crossinline block: (A) -> Unit): Boolean {
    var ran = false
    writeUnlocked { body ->
        if (body is A) {
            block(body)
            ran = true
        }
    }
    return ran
}
