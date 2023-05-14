import io.github.aecsocket.ignacio.*
import io.github.aecsocket.ignacio.physx.PhysxEngine
import io.github.aecsocket.ignacio.rapier.RapierEngine
import java.util.logging.Logger
import kotlin.test.Test

interface Collider



class HelloIgnacio {
    @Test
    fun helloRapier() {
        runTest(RapierEngine(RapierEngine.Settings()))
    }

    @Test
    fun helloPhysX() {
        runTest(PhysxEngine(PhysxEngine.Settings(), Logger.getAnonymousLogger()))
    }

    private fun runTest(engine: PhysicsEngine) {
        val physics = engine.createSpace(PhysicsSpace.Settings())

        val floorShape = engine.createShape(Cuboid(Vec(100.0, 0.5, 100.0)))

        val floor = physics.bodies.createFixed(
            position = Iso(),
            collider = engine.createCollider(ColliderDesc.Single(floorShape)),
        )
        val floorHandle = floor.handle
        floorHandle.read { floor ->
            val s = floor.collider()
        }

        floorHandle.read { floor ->
            val pos = floor.position
        }

        floorHandle.write { floor ->
            floor.position = Iso()
            // floor.destroy() // not owned
        }

        floorHandle.remove().destroy()

        val dt = 1.0 / 60.0
        repeat(200) {
            physics.startStep(dt)
            physics.finishStep()
            //println("[$it] ball @ ${ball.position.translation}")
        }

        physics.destroy()
        engine.destroy()
    }
}
