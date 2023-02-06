package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.math.Transform
import jolt.core.TempAllocator
import jolt.physics.Activation
import jolt.physics.PhysicsSystem
import jolt.kotlin.*
import jolt.physics.body.MotionType

class JtPhysicsSpace(
    private val engine: JoltEngine,
    val handle: PhysicsSystem,
    val tempAllocator: TempAllocator,
) : PhysicsSpace {
    fun destroy() {
        // todo destroy bodies
        handle.delete()
        tempAllocator.delete()
    }

    override fun addStaticBody(geometry: Geometry, transform: Transform): StaticBody {
        val settings = BodyCreationSettingsDp(
            engine.shapeOf(geometry),
            transform.position.jolt(),
            transform.rotation.jolt(),
            MotionType.STATIC,
            OBJECT_LAYER_NON_MOVING
        )
        val body = handle.bodyInterface.createBody(settings)
        handle.bodyInterface.addBody(body.id, Activation.DONT_ACTIVATE)
        return JtStaticBody(engine, this, BodyId(body.id))
    }

    override fun addDynamicBody(geometry: Geometry, transform: Transform, dynamics: BodyDynamics): DynamicBody {
        val settings = BodyCreationSettingsDp(
            engine.shapeOf(geometry),
            transform.position.jolt(),
            transform.rotation.jolt(),
            MotionType.DYNAMIC,
            OBJECT_LAYER_MOVING
        )
        val body = handle.bodyInterface.createBody(settings)
        handle.bodyInterface.addBody(body.id, Activation.ofValue(dynamics.activate))
        return JtDynamicBody(engine, this, BodyId(body.id))
    }

    override fun removeBody(body: PhysicsBody) {
        body as JtPhysicsBody
        handle.bodyInterface.removeBody(body.id)
    }

    override fun update(deltaTime: Float) {
        handle.update(
            deltaTime,
            engine.settings.spaces.collisionSteps,
            engine.settings.spaces.integrationSubSteps,
            tempAllocator,
            engine.jobSystem
        )
    }
}
