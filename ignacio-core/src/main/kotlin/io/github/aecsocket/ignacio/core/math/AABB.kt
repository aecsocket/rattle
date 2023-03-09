package io.github.aecsocket.ignacio.core.math

data class AABB(@JvmField val min: Vec3d, @JvmField val max: Vec3d) {
    operator fun plus(v: Vec3d) = AABB(min+v, max+v)
    operator fun plus(s: Double) = AABB(min+s, max+s)

    operator fun minus(v: Vec3d) = AABB(min-v, max-v)
    operator fun minus(s: Double) = AABB(min-s, max-s)

    operator fun times(v: Vec3d) = AABB(min*v, max*v)
    operator fun times(s: Double) = AABB(min*s, max*s)

    operator fun div(v: Vec3d) = AABB(min/v, max/v)
    operator fun div(s: Double) = AABB(min/s, max/s)

    fun center() = min.midpoint(max)

    fun extent() = max - min

    fun halfExtent() = (max - min) / 2.0
}

// inclusive on both ends
// an AABB of [(0, 0, 0), (1, 1, 1)] will give points:
//   (0, 0, 0), (1, 0, 0), (0, 1, 0), (1, 1, 0),
//   (0, 0, 1), (1, 0, 1), (0, 1, 1), (1, 1, 1)
fun AABB.points(): Iterable<Point3> {
    fun floor(s: Double): Int {
        val i = s.toInt()
        return if (s < i) i - 1 else i
    }

    fun ceil(s: Double): Int {
        val i = s.toInt()
        return if (s > i) i + 1 else i
    }

    val pMin = Point3(floor(min.x), floor(min.y), floor(min.z))
    val pMax = Point3(ceil(max.x), ceil(max.y), ceil(max.z))
    val extent = pMax - pMin
    val size = extent.x * extent.y * extent.z
    return object : Iterable<Point3> {
        override fun iterator() = object : Iterator<Point3> {
            var i = 0
            var dx = 0
            var dy = 0
            var dz = 0

            override fun hasNext() = i < size

            override fun next(): Point3 {
                if (i >= size)
                    throw IndexOutOfBoundsException("($dx, $dy, $dz)")
                val point = pMin + Point3(dx, dy, dz)
                dx += 1
                if (dx >= extent.x) {
                    dx = 0
                    dy += 1
                    if (dy >= extent.y) {
                        dy = 0
                        dz += 1
                    }
                }
                i += 1
                return point
            }
        }
    }
}
