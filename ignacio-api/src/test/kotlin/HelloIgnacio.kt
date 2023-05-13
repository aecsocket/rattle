import io.github.aecsocket.ignacio.*
import io.github.aecsocket.ignacio.rapier.RapierEngine
import io.github.aecsocket.ignacio.rapier.RapierShape
import kotlin.test.Test

class HelloIgnacio {
    @Test
    fun helloIgnacio() {
        runTest(RapierEngine())
    }

    private fun runTest(engine: IgnacioEngine) {
        val physics = engine.createSpace(PhysicsSpace.Settings())

        val floorShape = engine.createShape(Cuboid(Vec(100.0, 0.5, 100.0)))
        val floor = physics.colliders.create(ColliderDesc(
            shape = floorShape,
            position = Iso(),
        ))

        /*
        notes:
        - creating a shape does not mean you can share it
        - if so, double free happens
         */

        val ballShape = engine.createShape(Sphere(0.5))
        println("count = ${(ballShape as RapierShape).shape.strongCount()}")
        val ballCollider = physics.colliders.create(ColliderDesc(
            shape = ballShape,
        ))
        val ballBody = physics.rigidBodies.createMoving(MovingBodyDesc(
            position = Iso(Vec(0.0, 5.0, 0.0)),
            linearVelocity = Vec(0.0, 1.0, 0.0),
        ))
        ballCollider.attachTo(ballBody)

        val ball2s = engine.createShape(Sphere(0.5))
        val ball2 = physics.rigidBodies.createMoving(MovingBodyDesc(position = Iso(Vec(0.0, 6.5, 0.0))))
        val ball2c = physics.colliders.create(ColliderDesc(ball2s))
        ball2c.attachTo(ball2)

        val dt = 1.0 / 60.0
        repeat(200) {
            physics.step(dt)
            //println("[$it] ball @ ${ballBody.position}")
        }

        physics.destroy()
        engine.destroy()
    }
}
