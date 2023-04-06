package io.github.aecsocket.ignacio

import io.github.aecsocket.klam.*
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import java.util.function.Consumer

sealed interface BodyDescriptor {
    val shape: Shape
    val contactFilter: BodyContactFilter
    val isTrigger: Boolean
}

@ConfigSerializable
data class StaticBodyDescriptor(
    override val shape: Shape,
    override val contactFilter: BodyContactFilter,
    override val isTrigger: Boolean = false,
) : BodyDescriptor

sealed interface Mass {
    data class WithInertia(val mass: Float, val inertia: FMat4) : Mass

    data class Constant(val mass: Float) : Mass

    object Calculate : Mass
}

@ConfigSerializable
data class MovingBodyDescriptor(
    override val shape: Shape,
    override val contactFilter: BodyContactFilter,
    override val isTrigger: Boolean = false,
    val isKinematic: Boolean = false,
    val mass: Mass = Mass.Calculate,
    val linearVelocity: FVec3 = FVec3(0.0f),
    val angularVelocity: FVec3 = FVec3(0.0f),
    val friction: Float = 0.2f,
    val restitution: Float = 0.0f,
    val gravityFactor: Float = 1.0f,
    val linearDamping: Float = 0.05f,
    val angularDamping: Float = 0.05f,
    val maxLinearVelocity: Float = 500.0f,
    val maxAngularVelocity: Float = 0.25f * FPI * 60.0f,
) : BodyDescriptor

interface PhysicsBody {
    val isAdded: Boolean

    fun read(block: Consumer<Read>): Boolean

    fun readUnlocked(block: Consumer<Read>): Boolean

    fun write(block: Consumer<Write>): Boolean

    fun writeUnlocked(block: Consumer<Write>): Boolean

    interface Access {
        val key: PhysicsBody

        val isActive: Boolean

        val contactFilter: BodyContactFilter

        val position: DVec3

        val rotation: Quat

        val transform: Transform

        val bounds: DAabb3

        val shape: Shape

        val isTrigger: Boolean

        fun asDescriptor(): BodyDescriptor
    }

    interface Read : Access

    interface Write : Access {
        override var position: DVec3

        override var rotation: Quat

        override var transform: Transform

        override var shape: Shape

        override var isTrigger: Boolean
    }


    interface StaticAccess : Access {
        override fun asDescriptor(): StaticBodyDescriptor
    }

    interface StaticRead : StaticAccess, Read

    interface StaticWrite : StaticAccess, Write {}

    interface MovingAccess : Access {
        val isKinematic: Boolean

        val linearVelocity: FVec3

        val angularVelocity: FVec3

        val friction: Float

        val restitution: Float

        val gravityFactor: Float

        val linearDamping: Float

        val angularDamping: Float

        val maxLinearVelocity: Float

        val maxAngularVelocity: Float

        override fun asDescriptor(): MovingBodyDescriptor
    }

    interface MovingRead : MovingAccess, Read

    interface MovingWrite : MovingAccess, Write {
        override var isKinematic: Boolean

        override var linearVelocity: FVec3

        override var angularVelocity: FVec3

        override var friction: Float

        override var restitution: Float

        override var gravityFactor: Float

        override var linearDamping: Float

        override var angularDamping: Float

        override var maxLinearVelocity: Float

        override var maxAngularVelocity: Float

        fun activate()

        fun deactivate()

        fun applyForce(force: FVec3)

        fun applyForceAt(force: FVec3, at: DVec3)

        fun applyImpulse(impulse: FVec3)

        fun applyImpulseAt(impulse: FVec3, at: DVec3)

        fun applyTorque(torque: FVec3)

        fun applyAngularImpulse(impulse: FVec3)
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
