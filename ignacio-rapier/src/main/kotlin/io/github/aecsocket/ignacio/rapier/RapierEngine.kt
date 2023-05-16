package io.github.aecsocket.ignacio.rapier

import io.github.aecsocket.ignacio.*
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import rapier.Rapier
import rapier.geometry.CoefficientCombineRule
import rapier.shape.SharedShape

class RapierEngine(val settings: Settings) : PhysicsEngine {
    @ConfigSerializable
    data class Settings(
        val integration: Integration = Integration(),
    ) {
        @ConfigSerializable
        data class Integration(
            val minCcdDtMultiplier: Real = 0.01,
            val erp: Real = 0.8,
            val dampingRatio: Real = 0.25,
            val jointErp: Real = 1.0,
            val jointDampingRatio: Real = 1.0,
            val allowedLinearError: Real = 0.001,
            val maxPenetrationCorrection: Real = Real.MAX_VALUE,
            val predictionDistance: Real = 0.002,
            val maxVelocityIterations: Long = 4,
            val maxVelocityFrictionIterations: Long = 8,
            val maxStabilizationIterations: Long = 1,
            val interleaveRestitutionAndFrictionResolution: Boolean = true,
            val minIslandSize: Long = 128,
            val maxCcdSubsteps: Long = 1,
        )
    }

    override val name = "Rapier"

    private val destroyed = DestroyFlag()
    override lateinit var version: String
        private set

    init {
        Rapier.load()
        version = Rapier.VERSION
    }

    override fun destroy() {
        destroyed()
    }

    override fun createMaterial(
        friction: Real,
        restitution: Real,
        frictionCombine: CoeffCombineRule,
        restitutionCombine: CoeffCombineRule,
    ): PhysicsMaterial {
        fun CoeffCombineRule.asRapier() = when (this) {
            CoeffCombineRule.AVERAGE -> CoefficientCombineRule.AVERAGE
            CoeffCombineRule.MIN -> CoefficientCombineRule.MIN
            CoeffCombineRule.MULTIPLY -> CoefficientCombineRule.MULTIPLY
            CoeffCombineRule.MAX -> CoefficientCombineRule.MAX
        }

        return RapierMaterial(
            friction,
            restitution,
            frictionCombine.asRapier(),
            restitutionCombine.asRapier(),
        )
    }

    override fun createShape(geom: Geometry): Shape {
        val handle = pushArena { arena ->
            when (geom) {
                is Sphere -> SharedShape.of(
                    rapier.shape.Ball.of(arena, geom.radius)
                )
                is Box -> SharedShape.of(
                    rapier.shape.Cuboid.of(arena, geom.halfExtent.toVector(arena))
                )
                is Capsule -> SharedShape.of(
                    rapier.shape.Capsule.of(
                        arena,
                        rapier.shape.Segment.of(
                            arena,
                            Vec(0.0, -geom.halfHeight, 0.0).toVector(arena),
                            Vec(0.0,  geom.halfHeight, 0.0).toVector(arena),
                        ),
                        geom.radius,
                    )
                )
            }
        }
        return RapierShape(handle)
    }

    override fun createSpace(settings: PhysicsSpace.Settings): PhysicsSpace {
        return RapierSpace(this, settings)
    }
}
