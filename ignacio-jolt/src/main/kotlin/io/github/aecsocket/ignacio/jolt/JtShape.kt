package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.core.DestroyFlag
import io.github.aecsocket.ignacio.core.Shape
import jolt.physics.collision.shape.ConvexShape
import jolt.physics.collision.shape.ShapeType

class JtShape(val handle: JShape) : Shape {
    private val destroyed = DestroyFlag()
    val convexHandle = if (handle.type == ShapeType.CONVEX) ConvexShape.at(handle.address()) else null

    override val density by lazy { convexHandle?.density }

    override fun destroy() {
        destroyed.mark()
        handle.destroy()
    }
}
