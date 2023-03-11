package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.core.DestroyFlag
import io.github.aecsocket.ignacio.core.Shape
import jolt.physics.collision.shape.ConvexShape
import jolt.physics.collision.shape.ShapeType

class JtShape(val handle: JShape) : Shape {
    private val destroyed = DestroyFlag()
    private val convexHandle = if (handle.type == ShapeType.CONVEX) ConvexShape.at(handle.address()) else null

    override fun destroy() {
        destroyed.mark()
        handle.destroy()
    }

    override val density: Float?
        get() = convexHandle?.density
}
