package io.github.aecsocket.rattle.world

import io.github.aecsocket.klam.*
import io.github.aecsocket.rattle.*
import org.spongepowered.configurate.objectmapping.ConfigSerializable

abstract class Dt(
    val settings: Settings,
    val physics: PhysicsSpace,
) : TerrainStrategy {
    @ConfigSerializable
    data class Settings(
        val expandVelocity: Double = 0.1,
        val expandConstant: Double = 4.0,
    )

    override fun onPhysicsStep() {
        fun processColl(linVel: DVec3, coll: Collider) {
            val bounds = expandBounds(linVel, coll)

        }

        physics.rigidBodies.active().forEach body@ { bodyKey ->
            val body = physics.rigidBodies.read(bodyKey) ?: return@body
            val linVel = body.linearVelocity
            body.colliders.forEach coll@ { collKey ->
                val coll = physics.colliders.read(collKey) ?: return@coll
                processColl(linVel, coll)
            }
        }
    }

    // the mostly safe stuff

    private fun expandBounds(linVel: DVec3, coll: Collider): DAabb3 {
        val from = coll.position.translation
        val to = from + linVel * settings.expandVelocity
        val collBound = coll.bounds()

        return expand(
            expand(
                DAabb3(
                    min(from, to),
                    max(from, to),
                ), // a box spanning the current coll pos, up to a bit in front of it (determined by velocity)
                (collBound.max - collBound.min) / 2.0,
            ), // that velocity box, expanded by the actual collider bounds
            DVec3(settings.expandConstant), // that box, expanded by the constant factor
        )
    }
}
