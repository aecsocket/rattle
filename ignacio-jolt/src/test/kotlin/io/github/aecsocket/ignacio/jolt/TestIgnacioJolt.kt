package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.*
import kotlin.test.Test

class TestIgnacioJolt {
    @Test
    fun test() {
        val engine = JoltEngine(JoltEngine.Settings())
        val physics = engine.createSpace(PhysicsSpace.Settings())

        val floorShape = engine.createShape(BoxGeometry(Vec3(100.0f, 0.5f, 100.0f)))
        val floorBody = physics.addStaticBody(StaticBodyDescriptor(
            shape = floorShape,
            objectLayer = engine.objectLayers.static,
        ), Transform())

        val ballShape = engine.createShape(SphereGeometry(1.0f))
        val ballBody = physics.addMovingBody(MovingBodyDescriptor(
            shape = ballShape,
            objectLayer = engine.objectLayers.moving,
            linearVelocity = Vec3(0.0f, 2.0f, 0.0f),
            restitution = 0.5f,
        ), Transform(RVec3(0.0, 5.0, 0.0)))

        ballBody.writeAs<PhysicsBody.MovingWrite> { ball ->
            ball.activate()
        }

        repeat(200) { step ->
            physics.update(1.0f / 60.0f)

            ballBody.readAs<PhysicsBody.MovingRead> { ball ->
                println("[$step] $ballBody @ ${ball.position}")
            }
        }

        physics.removeBodies(setOf(ballBody, floorBody))
        ballShape.destroy()
        floorShape.destroy()
        physics.destroy()
        engine.destroy()
    }
}
