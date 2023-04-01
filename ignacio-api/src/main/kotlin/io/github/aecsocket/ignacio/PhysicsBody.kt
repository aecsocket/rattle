package io.github.aecsocket.ignacio

import io.github.aecsocket.klam.FPI
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import java.util.function.Consumer

sealed interface BodyDescriptor {
    val shape: Shape
    val contactFilter: BodyContactFilter
    val trigger: Boolean
}

@ConfigSerializable
data class StaticBodyDescriptor(
    override val shape: Shape,
    override val contactFilter: BodyContactFilter,
    override val trigger: Boolean = false,
) : BodyDescriptor

@ConfigSerializable
data class MovingBodyDescriptor(
    override val shape: Shape,
    override val contactFilter: BodyContactFilter,
    override val trigger: Boolean = false,
    val kinematic: Boolean = false,
    val friction: Float = 0.2f,
    val restitution: Float = 0.0f,
    val linearDamping: Float = 0.05f,
    val angularDamping: Float = 0.05f,
    val maxLinearVelocity: Float = 500.0f,
    val maxAngularVelocity: Float = 0.25f * FPI * 60.0f,
    val gravityFactor: Float = 1.0f,
    val linearVelocity: Vec3 = Vec3(0.0f),
    val angularVelocity: Vec3 = Vec3(0.0f),
) : BodyDescriptor

interface PhysicsBody {
    val added: Boolean

    fun read(block: Consumer<Read>): Boolean

    fun readUnlocked(block: Consumer<Read>): Boolean

    fun write(block: Consumer<Write>): Boolean

    fun writeUnlocked(block: Consumer<Write>): Boolean

    interface Access {
        val key: PhysicsBody

        val active: Boolean

        val contactFilter: BodyContactFilter

        val position: RVec3

        val rotation: Quat

        val transform: Transform

        // TODO val bounds: AABB

        val shape: Shape

        val trigger: Boolean

        fun asDescriptor(): BodyDescriptor
    }

    interface Read : Access

    interface Write : Access {
        override var position: RVec3

        override var rotation: Quat

        override var transform: Transform

        override var shape: Shape

        override var trigger: Boolean
    }


    interface StaticAccess : Access {
        override fun asDescriptor(): StaticBodyDescriptor
    }

    interface StaticRead : StaticAccess, Read

    interface StaticWrite : StaticAccess, Write {}

    interface MovingAccess : Access {
        val kinematic: Boolean

        val linearVelocity: Vec3

        val angularVelocity: Vec3

        val gravityFactor: Float

        override fun asDescriptor(): MovingBodyDescriptor
    }

    interface MovingRead : MovingAccess, Read

    interface MovingWrite : MovingAccess, Write {
        override var kinematic: Boolean

        override var linearVelocity: Vec3

        override var angularVelocity: Vec3

        override var gravityFactor: Float

        fun activate()

        fun deactivate()

        fun applyForce(force: Vec3)

        fun applyForceAt(force: Vec3, at: RVec3)

        fun applyImpulse(impulse: Vec3)

        fun applyImpulseAt(impulse: Vec3, at: RVec3)

        fun applyTorque(torque: Vec3)

        fun applyAngularImpulse(impulse: Vec3)
    }
}

inline fun <reified A: PhysicsBody.Read> PhysicsBody.readAs(crossinline block: (A) -> Unit): Boolean {
    var success = false
    read { body ->
        if (body is A) {
            block(body)
            success = true
        }
    }
    return success
}

inline fun <reified A : PhysicsBody.Read> PhysicsBody.readUnlockedAs(crossinline block: (A) -> Unit): Boolean {
    var success = false
    readUnlocked { body ->
        if (body is A) {
            block(body)
            success = true
        }
    }
    return success
}

inline fun <reified A : PhysicsBody.Write> PhysicsBody.writeAs(crossinline block: (A) -> Unit): Boolean {
    var success = false
    write { body ->
        if (body is A) {
            block(body)
            success = true
        }
    }
    return success
}

inline fun <reified A : PhysicsBody.Write> PhysicsBody.writeUnlockedAs(crossinline block: (A) -> Unit): Boolean {
    var success = false
    writeUnlocked { body ->
        if (body is A) {
            block(body)
            success = true
        }
    }
    return success
}
