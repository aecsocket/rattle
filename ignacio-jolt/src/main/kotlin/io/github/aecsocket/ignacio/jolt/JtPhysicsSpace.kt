package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.math.Transform
import jolt.core.TempAllocatorImpl
import jolt.physics.Activation
import jolt.physics.PhysicsSystem
import jolt.kotlin.*
import jolt.physics.body.BodyCreationSettings
import jolt.physics.body.MotionType
import jolt.physics.collision.shape.BoxShape
import jolt.physics.collision.shape.CapsuleShape
import jolt.physics.collision.shape.Shape
import jolt.physics.collision.shape.SphereShape

class JtPhysicsSpace(
    private val engine: JoltEngine,
    val handle: PhysicsSystem
) : PhysicsSpace {
    val tempAllocator = TempAllocatorImpl.ofSize(10 * 1024 * 1024)

    fun destroy() {
        // todo destroy bodies
        handle.delete()
        tempAllocator.delete()
    }

    fun shapeOf(geometry: Geometry): Shape {
        return when (geometry) {
            is SphereGeometry -> SphereShape(geometry.radius)
            is BoxGeometry -> BoxShape(geometry.halfExtent.jolt())
            is CapsuleGeometry -> CapsuleShape(geometry.halfHeight, geometry.radius)
            else -> throw IllegalArgumentException("Unsupported geometry type ${geometry::class.simpleName}")
        }
    }

    override fun addStaticBody(geometry: Geometry, transform: Transform): StaticBody {
        val settings = BodyCreationSettings.dp(
            shapeOf(geometry),
            transform.position.jolt(),
            transform.rotation.jolt(),
            MotionType.STATIC,
            LAYER_NON_MOVING
        )
        val body = handle.bodyInterface.createBody(settings)
        handle.bodyInterface.addBody(body.id, Activation.DONT_ACTIVATE)
        return JtStaticBody(engine, this, BodyId(body.id))
    }

    override fun addDynamicBody(geometry: Geometry, transform: Transform): DynamicBody {
        val settings = BodyCreationSettings.dp(
            shapeOf(geometry),
            transform.position.jolt(),
            transform.rotation.jolt(),
            MotionType.DYNAMIC,
            LAYER_MOVING
        )
        val body = handle.bodyInterface.createBody(settings)
        handle.bodyInterface.addBody(body.id, Activation.ACTIVATE)
        return JtDynamicBody(engine, this, BodyId(body.id))
    }

    override fun removeBody(body: PhysicsBody) {
        body as JtPhysicsBody
        handle.bodyInterface.removeBody(body.id)
    }

    override fun update(deltaTime: Float) {
        handle.update(deltaTime, 1, 1, tempAllocator, engine.jobSystem)
    }
}
