package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.Quat
import io.github.aecsocket.klam.DVec3
import io.github.aecsocket.klam.FVec3
import java.lang.foreign.MemorySession

typealias JtFVec3 = jolt.math.FVec3
typealias JtDVec3 = jolt.math.DVec3
typealias JtQuat = jolt.math.Quat

fun <R> pushMemory(block: (arena: MemorySession) -> R): R {
    return MemorySession.openConfined().use(block)
}

fun MemorySession.JtFVec3() = JtFVec3.of(this)
fun MemorySession.asJolt(v: FVec3): JtFVec3 = JtFVec3.of(this, v.x, v.y, v.z)
fun JtFVec3.asIgnacio() = FVec3(x, y, z)

fun MemorySession.JtDVec3() = JtDVec3.of(this)
fun MemorySession.asJolt(v: DVec3): JtDVec3 = JtDVec3.of(this, v.x, v.y, v.z)
fun JtDVec3.asIgnacio() = DVec3(x, y, z)

fun MemorySession.JtQuat() = JtQuat.of(this)
fun MemorySession.asJolt(q: Quat): JtQuat = JtQuat.of(this, q.x, q.y, q.z, q.w)
fun JtQuat.asIgnacio() = Quat(x, y, z, w)
