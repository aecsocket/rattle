package io.gitlab.aecsocket.ignacio.core

import io.gitlab.aecsocket.ignacio.core.math.AABB

data class TerrainPos(val x: Int, val y: Int, val z: Int)

private fun floor(x: IgScalar): Int {
    val i = x.toInt()
    return if (x < i) i - 1 else i
}

fun terrainPosIn(box: AABB): Array<TerrainPos> {
    val (ax, ay, az) = box.min
    val (bx, by, bz) = box.max
    val x1 = floor(ax); val y1 = floor(ay); val z1 = floor(az)
    val x2 = floor(bx); val y2 = floor(by); val z2 = floor(bz)

    // BlockPos#betweenClosed
    val dx = x2 - x1 + 1; val dy = y2 - y1 + 1; val dz = z2 - z1 + 1
    return Array(dx * dy * dz) { i ->
        val ox = i % dx
        val u = i / dx
        val oy = u % dy
        val oz = u / dy
        TerrainPos(x1 + ox, y1 + oy, z1 + oz)
    }
}
