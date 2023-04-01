package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.*
import kotlin.test.Test

class TestIgnacioJolt {
    @Test
    fun test() {
        val engineBuilder = JoltEngine.Builder(JoltEngine.Settings())
        val engine = engineBuilder.build()

        val physics = engine.space(PhysicsSpace.Settings())

        val floorShape = engine.shape(BoxGeometry(Vec3(100.0f, 0.5f, 100.0f)))
        val floorBody = physics.bodies.addStatic(StaticBodyDescriptor(
            shape = floorShape,
            contactFilter = engine.contactFilter(engine.layers.static),
        ), Transform())

        val ballShape = engine.shape(SphereGeometry(1.0f))
        val ballBody = physics.bodies.addMovingBody(MovingBodyDescriptor(
            shape = ballShape,
            contactFilter = engine.contactFilter(engine.layers.moving),
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

        println("Bodies within 16m of (0, 0, 0): ${physics.broadQuery.contactSphere(RVec3(0.0, 0.0, 0.0), 16.0f, engine.filters.anyLayer)}")

        physics.bodies.removeAll(setOf(ballBody, floorBody))
        ballShape.destroy()
        floorShape.destroy()
        physics.destroy()
        engine.destroy()
    }
}
