package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.klam.*
import jolt.geometry.AABox
import jolt.math.DMat44
import jolt.math.FMat44
import jolt.physics.collision.DRayCast
import jolt.physics.collision.FRayCast
import java.lang.foreign.MemorySession

typealias JtFVec3 = jolt.math.FVec3
typealias JtDVec3 = jolt.math.DVec3
typealias JtQuat = jolt.math.Quat

fun openArena(): MemorySession {
    return MemorySession.openShared()
}

fun <R> pushArena(block: (arena: MemorySession) -> R): R {
    return MemorySession.openConfined().use(block)
}

fun MemorySession.FVec3() = JtFVec3.of(this)
fun MemorySession.asJolt(v: FVec3): JtFVec3 = JtFVec3.of(this, v.x, v.y, v.z)
fun JtFVec3.asIgnacio() = FVec3(x, y, z)

fun MemorySession.DVec3() = JtDVec3.of(this)
fun MemorySession.asJolt(v: DVec3): JtDVec3 = JtDVec3.of(this, v.x, v.y, v.z)
fun JtDVec3.asIgnacio() = DVec3(x, y, z)

fun MemorySession.Quat() = JtQuat.of(this)
fun MemorySession.asJolt(q: FQuat): JtQuat = JtQuat.of(this, q.x, q.y, q.z, q.w)
fun JtQuat.asIgnacio() = FQuat(x, y, z, w)

fun MemorySession.FMat44() = FMat44.of(this)
fun MemorySession.asJolt(m: FMat4): FMat44 = FMat44.of(this,
    m[0, 0], m[0, 1], m[0, 2], m[0, 3],
    m[1, 0], m[1, 1], m[1, 2], m[1, 3],
    m[2, 0], m[2, 1], m[2, 2], m[2, 3],
    m[3, 0], m[3, 1], m[3, 2], m[3, 3],
)

fun MemorySession.DMat44() = DMat44.of(this)
fun MemorySession.asJolt(m: DMat4): DMat44 = DMat44.of(this,
    m[0, 0].toFloat(), m[0, 1].toFloat(), m[0, 2].toFloat(), m[0, 3],
    m[1, 0].toFloat(), m[1, 1].toFloat(), m[1, 2].toFloat(), m[1, 3],
    m[2, 0].toFloat(), m[2, 1].toFloat(), m[2, 2].toFloat(), m[2, 3],
    m[3, 0].toFloat(), m[3, 1].toFloat(), m[3, 2].toFloat(),
)

fun MemorySession.asJolt(r: DRay3): DRayCast = DRayCast.of(this, asJolt(r.origin), asJolt(r.direction))

fun MemorySession.asJoltF(r: DRay3): FRayCast = FRayCast.of(this, asJolt(FVec3(r.origin)), asJolt(r.direction))

fun MemorySession.AABox(): AABox = AABox.of(this)
fun MemorySession.asJolt(b: FAabb3): AABox = AABox.of(this, asJolt(b.min), asJolt(b.max))
fun MemorySession.asJoltF(b: DAabb3): AABox = AABox.of(this, asJolt(FVec3(b.min)), asJolt(FVec3(b.max)))
fun AABox.asIgnacio() = FAabb3(min.asIgnacio(), max.asIgnacio())
fun AABox.asIgnacioD() = DAabb3(
    DVec3(min.asIgnacio()),
    DVec3(max.asIgnacio())
)
