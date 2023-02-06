package io.github.aecsocket.ignacio.core

import io.github.aecsocket.ignacio.core.math.Transform
import io.github.aecsocket.ignacio.core.math.Vec3d
import io.github.aecsocket.ignacio.core.math.Vec3f
import io.github.aecsocket.ignacio.jolt.JoltEngine
import io.github.aecsocket.ignacio.jolt.JtPhysicsSpace
import io.github.aecsocket.ignacio.physx.PhysxEngine
import java.util.logging.Logger
import kotlin.random.Random
import kotlin.test.Test

class ComparisonTest {
    @Test
    fun doTest() {
        val jolt = JoltEngine(
            JoltEngine.Settings(
                jobs = JoltEngine.Settings.Jobs(
                ),
                spaces = JoltEngine.Settings.Spaces(
                ),
                physics = JoltEngine.Settings.Physics(

                )
            )
        )
        val physx = PhysxEngine(
            PhysxEngine.Settings(),
            Logger.getAnonymousLogger()
        )

        data class TestCase(
            val piles: Int,
            val boxesPerPile: Int,
            val time: Float
        )

        fun runTest(engine: IgnacioEngine, case: TestCase): Double {
            val space = engine.createSpace(PhysicsSpace.Settings())
            val ground = space.addStaticBody(
                BoxGeometry(Vec3f(30_000_000f, 0.5f, 30_000_000f)),
                Transform.Identity
            )

            val boxes = Array<PhysicsBody>(case.piles * case.boxesPerPile) {
                val offset = (it / case.boxesPerPile) * 150.0 // 150m: distance between piles
                val position = Vec3d(
                    offset + -50.0 + Random.nextDouble() * 100.0,
                    25.0 + Random.nextDouble() * 50.0,
                    -50.0 + Random.nextDouble() * 100.0,
                )
                space.addDynamicBody(
                    BoxGeometry(Vec3f(0.5f)),
                    Transform(position),
                    BodyDynamics(activate = true)
                )
            }

            if (space is JtPhysicsSpace) {
                space.handle.optimizeBroadPhase()
            }

            val deltaTime = 1 / 60f
            val start = System.nanoTime()
            repeat((case.time / deltaTime).toInt()) {
                space.update(deltaTime)
            }
            val end = System.nanoTime()
            val delta = (end - start) / 1.0e6

            boxes.forEach { it.destroy() }
            ground.destroy()
            engine.destroySpace(space)

            return delta
        }

        println(" #  | Piles | Boxes/pile | Time (s) | Jolt (ms)  | PhysX (ms) ")
        println("----+-------+------------+----------+------------+------------")
        listOf(
            TestCase(1, 100, 20f),
            TestCase(1, 1000, 20f),
            TestCase(1, 5000, 10f),

            TestCase(10, 1000, 20f),
            //TestCase(10, 5000, 10f),

            TestCase(100, 100, 10f),

            //TestCase(50, 1000, 15f),
        ).forEachIndexed { idx, case ->
            val joltTime = runTest(jolt, case)
            val physxTime = runTest(physx, case)
            println(" %2d | %5d | %10d | %8.1f | %10.3f | %10.3f ".format(idx+1, case.piles, case.boxesPerPile, case.time, joltTime, physxTime))
        }
    }
}
