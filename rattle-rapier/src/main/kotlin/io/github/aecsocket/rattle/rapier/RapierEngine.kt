package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.rattle.*
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import rapier.Rapier
import rapier.data.ArenaKey
import rapier.dynamics.RigidBodyBuilder
import rapier.dynamics.RigidBodyType
import rapier.geometry.ColliderBuilder
import rapier.shape.SharedShape

@JvmInline
value class ArenaKey(val id: Long) {
    override fun toString(): String = ArenaKey.asString(id)
}

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
        return RapierMaterial(
            friction,
            restitution,
            frictionCombine,
            restitutionCombine,
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

    override fun createCollider(
        shape: Shape,
        material: PhysicsMaterial,
        position: Iso,
        isSensor: Boolean,
    ): Collider {
        shape as RapierShape
        val coll = pushArena { arena ->
            ColliderBuilder.of(shape.acquire().handle)
                .friction(material.friction)
                .restitution(material.restitution)
                .frictionCombineRule(material.frictionCombine.convert())
                .restitutionCombineRule(material.restitutionCombine.convert())
                .position(position.toIsometry(arena))
                .sensor(isSensor)
                .density(1.0)
                .use { it.build() }
        }
        return RapierCollider(RapierCollider.State.Removed(coll))
    }

    private fun createBody(builder: RigidBodyBuilder): RapierBody {
        val body = builder.use { it.build() }
        return RapierBody(RapierBody.State.Removed(body))
    }

    override fun createFixedBody(
        position: Iso,
    ): FixedBody {
        return createBody(pushArena { arena ->
            RigidBodyBuilder.fixed()
                .position(position.toIsometry(arena))
        })
    }

    override fun createMovingBody(
        position: Iso,
        isKinematic: Boolean,
        isCcdEnabled: Boolean,
        linearVelocity: Vec,
        angularVelocity: Vec,
        gravityScale: Real,
        linearDamping: Real,
        angularDamping: Real,
        sleeping: Sleeping,
    ): MovingBody {
        return createBody(pushArena { arena ->
            RigidBodyBuilder.of(if (isKinematic) RigidBodyType.KINEMATIC_POSITION_BASED else RigidBodyType.DYNAMIC)
                .position(position.toIsometry(arena))
                .ccdEnabled(isCcdEnabled)
                .linvel(linearVelocity.toVector(arena))
                .angvel(angularVelocity.toAngVector(arena))
                .gravityScale(gravityScale)
                .linearDamping(linearDamping)
                .angularDamping(angularDamping)
                .apply {
                    when (sleeping) {
                        is Sleeping.Disabled -> canSleep(false)
                        is Sleeping.Enabled -> {
                            canSleep(true)
                            sleeping(sleeping.state)
                        }
                    }
                }
        })
    }

    override fun createSpace(settings: PhysicsSpace.Settings): PhysicsSpace {
        return RapierSpace(this, settings)
    }
}
