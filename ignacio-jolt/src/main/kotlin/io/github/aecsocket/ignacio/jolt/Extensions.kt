package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.alexandria.core.math.*
import io.github.aecsocket.alexandria.core.math.Quat
import jolt.Destroyable
import jolt.geometry.AABox
import jolt.math.*
import jolt.physics.body.Body
import jolt.physics.body.BodyIds
import jolt.physics.body.BodyLockInterface
import jolt.physics.body.BodyLockRead
import jolt.physics.collision.DRayCast
import java.lang.foreign.MemorySession
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

typealias JQuat = jolt.math.Quat
typealias JContactListener = jolt.physics.collision.ContactListener
typealias JContactListenerFn = jolt.physics.collision.ContactListenerFn.D
typealias JShape = jolt.physics.collision.shape.Shape
typealias JContactManifold = jolt.physics.collision.ContactManifold

@JvmInline
value class JObjectLayer(val id: Short)

@JvmInline
value class JBroadPhaseLayer(val id: Byte)

@JvmInline
value class BodyId(val id: Int) {
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
fun AABox() = AABox.of(this@MemorySession)
context(MemorySession)
fun AABB.toJoltF() = AABox.of(this@MemorySession, Vec3f(min).toJolt(), Vec3f(max).toJolt())

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
    ).toQuat()
    return Transform(position, rotation)
}

context(MemorySession)
fun Ray.toJolt(distance: Float) = DRayCast.of(this@MemorySession, origin.toJolt(), (direction * distance).toJolt())

context(MemorySession)
fun BodyId.lockRead(iface: BodyLockInterface, block: (Body) -> Unit) {
    val lock = BodyLockRead.of(this@MemorySession)
    iface.lockRead(id, lock)
    lock.body?.let(block)
    iface.unlockRead(lock)
}
