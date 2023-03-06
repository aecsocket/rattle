package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.core.math.*
import io.github.aecsocket.ignacio.core.math.Quat
import jolt.Destroyable
import jolt.math.*
import jolt.physics.body.BodyIds
import jolt.physics.collision.DRayCast
import java.lang.foreign.MemorySession
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

typealias JQuat = jolt.math.Quat
typealias JContactListener = jolt.physics.collision.ContactListener
typealias JContactListenerFn = jolt.physics.collision.ContactListenerFn.D

@JvmInline
value class JObjectLayer(val id: Short)

@JvmInline
value class JBroadPhaseLayer(val layer: Byte)

@JvmInline
value class JBodyId(val id: Int) {
    override fun toString(): String = BodyIds.asString(id)
}

@OptIn(ExperimentalContracts::class)
fun <R> useMemory(block: MemorySession.() -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return MemorySession.openConfined().use(block)
}

fun <T : Destroyable, R> T.use(block: (T) -> R): R {
    val result = block(this)
    destroy()
    return result
}

context(MemorySession)
fun FVec3() = FVec3.of(this@MemorySession)
context(MemorySession)
fun Vec3f.toJolt() = FVec3.of(this@MemorySession, x, y, z)
fun FVec3.toIgnacio() = Vec3f(x, y, z)

context(MemorySession)
fun DVec3() = DVec3.of(this@MemorySession)
context(MemorySession)
fun Vec3d.toJolt() = DVec3.of(this@MemorySession, x, y, z)
fun DVec3.toIgnacio() = Vec3d(x, y, z)

context(MemorySession)
fun JQuat() = JQuat.of(this@MemorySession)
context(MemorySession)
fun Quat.toJolt() = JQuat.of(this@MemorySession, x, y, z, w)
fun JQuat.toIgnacio() = Quat(x, y, z, w)

context(MemorySession)
fun FMat44() = FMat44.of(this@MemorySession)

context(MemorySession)
fun DMat44() = DMat44.of(this@MemorySession)
fun DMat44.toTransform(): Transform {
    val position = Vec3d(getTranslation(0), getTranslation(1), getTranslation(2))
    val rotation = Mat3f(
        getRotation(0), getRotation(4), getRotation(8),
        getRotation(1), getRotation(5), getRotation(9),
        getRotation(2), getRotation(6), getRotation(10)
    ).quat()
    return Transform(position, rotation)
}

context(MemorySession)
fun Ray.toJolt(distance: Float) = DRayCast.of(this@MemorySession, origin.toJolt(), (direction * distance).toJolt())
