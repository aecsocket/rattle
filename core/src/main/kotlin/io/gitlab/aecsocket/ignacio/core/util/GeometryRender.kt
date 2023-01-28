package io.gitlab.aecsocket.ignacio.core.util

import io.gitlab.aecsocket.ignacio.core.*
import io.gitlab.aecsocket.ignacio.core.math.Vec3
import io.gitlab.aecsocket.ignacio.core.math.clamp
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val PI2 = PI * 2

fun interface PointRenderer {
    fun render(pos: Vec3)
}

typealias RenderPoints = List<Vec3>

@ConfigSerializable
data class GeometryRender(
    val lineMinPoints: Int = 5,
    val lineMaxPoints: Int = 80,
    val linePointInterval: Double = 0.2,
    val sphereMinPoints: Int = 10,
    val sphereMaxPoints: Int = 80,
    val spherePointInterval: Double = 0.2,
) {
    fun line(from: Vec3, to: Vec3): RenderPoints {
        val delta = to - from
        val distance = delta.length()
        val numPoints = clamp((distance / linePointInterval).toInt(), lineMinPoints, lineMaxPoints)
        val step = delta / numPoints.toDouble()

        return Array(numPoints) {
            from + (step * it.toDouble())
        }.toList()
    }

    fun plane(plane: IgPlaneGeometry): RenderPoints {
        // all planes face the +X direction
        return line(Vec3.Zero, Vec3.X)
    }

    fun sphere(sphere: IgSphereGeometry): RenderPoints {
        fun map(x: Int, from: Double, to: Double): Double {
            val proportion = x / from
            return proportion * to
        }

        val radius = sphere.radius
        val numPoints = clamp((radius / spherePointInterval).toInt(), sphereMinPoints, sphereMaxPoints)

        val total = numPoints + 1
        val dTotal = numPoints.toDouble()
        return Array(total * total) {
            val lat = map(it / total, dTotal, PI)
            val lon = map(it % total, dTotal, PI2)
            Vec3(
                radius * sin(lat) * cos(lon),
                radius * cos(lat),
                radius * sin(lat) * sin(lon)
            )
        }.toList()
    }

    fun box(box: IgBoxGeometry): RenderPoints {
        val min = -box.halfExtent
        val max = box.halfExtent
        val b0 = min
        val b1 = Vec3(max.x, min.y, min.z)
        val b2 = Vec3(max.x, min.y, max.z)
        val b3 = Vec3(min.x, min.y, max.z)
        val t0 = Vec3(min.x, max.y, min.z)
        val t1 = Vec3(max.x, max.y, min.z)
        val t2 = max
        val t3 = Vec3(min.x, max.y, max.z)

        return line(b0, b1) + line(b1, b2) + line(b2, b3) + line(b3, b0) +
            line(t0, t1) + line(t1, t2) + line(t2, t3) + line(t3, t0) +
            line(b0, t0) + line(b1, t1) + line(b2, t2) + line(b3, t3)
    }

    fun capsule(capsule: IgCapsuleGeometry): RenderPoints {
        return emptyList() // TODO
    }

    fun points(geometry: IgGeometry): RenderPoints {
        return when (geometry) {
            is IgPlaneGeometry -> plane(geometry)
            is IgSphereGeometry -> sphere(geometry)
            is IgBoxGeometry -> box(geometry)
            is IgCapsuleGeometry -> capsule(geometry)
        }
    }
}
