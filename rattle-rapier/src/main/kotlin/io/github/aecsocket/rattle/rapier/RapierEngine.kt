package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.rattle.*
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import rapier.Rapier
import rapier.dynamics.RigidBodyBuilder
import rapier.dynamics.joint.GenericJoint
import rapier.geometry.ColliderBuilder
import rapier.pipeline.PhysicsPipeline
import rapier.shape.Segment
import rapier.shape.SharedShape

class RapierEngine(var settings: Settings) : PhysicsEngine {
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
                        when (geom.axis) {
                            LinAxis.X -> Segment.of(
                                arena,
                                Vec(-geom.halfHeight, 0.0, 0.0).toVector(arena),
                                Vec( geom.halfHeight, 0.0, 0.0).toVector(arena),
                            )
                            LinAxis.Y -> Segment.of(
                                arena,
                                Vec(0.0, -geom.halfHeight, 0.0).toVector(arena),
                                Vec(0.0,  geom.halfHeight, 0.0).toVector(arena),
                            )
                            LinAxis.Z -> Segment.of(
                                arena,
                                Vec(0.0, 0.0, -geom.halfHeight).toVector(arena),
                                Vec(0.0, 0.0,  geom.halfHeight).toVector(arena),
                            )
                        },
                        geom.radius,
                    )
                )
                is Cylinder -> SharedShape.of(
                    rapier.shape.Cylinder.of(arena, geom.halfHeight, geom.radius)
                )
                is Cone -> SharedShape.of(
                    rapier.shape.Cone.of(arena, geom.halfHeight, geom.radius)
                )
            }
        }
        return RapierShape(handle)
    }

    override fun createCollider(
        shape: Shape,
        material: PhysicsMaterial,
        position: Iso,
        mass: Mass,
        physics: PhysicsMode,
    ): Collider.Own {
        shape as RapierShape
        val coll = pushArena { arena ->
            // do not manually acquire the shape here; Rapier will increment the Arc ref count itself
            ColliderBuilder.of(shape.handle)
                .friction(material.friction)
                .restitution(material.restitution)
                .frictionCombineRule(material.frictionCombine.convert())
                .restitutionCombineRule(material.restitutionCombine.convert())
                .position(position.toIsometry(arena))
                .sensor(when (physics) {
                    PhysicsMode.SOLID -> false
                    PhysicsMode.SENSOR -> true
                })
                .use {
                    when (mass) {
                        is Mass.Constant -> it.mass(mass.mass)
                        is Mass.Density -> it.density(mass.density)
                    }
                    it.build()
                }
        }
        return RapierCollider.Own(coll)
    }

    override fun createBody(
        position: Iso,
        type: RigidBodyType,
        linearVelocity: Vec,
        angularVelocity: Vec,
        isCcdEnabled: Boolean,
        gravityScale: Real,
        linearDamping: Real,
        angularDamping: Real,
        sleeping: Sleeping
    ): RigidBody.Own {
        val body = pushArena { arena ->
            RigidBodyBuilder.of(type.convert())
                .position(position.toIsometry(arena))
                .linvel(linearVelocity.toVector(arena))
                .angvel(angularVelocity.toAngVector(arena))
                .ccdEnabled(isCcdEnabled)
                .gravityScale(gravityScale)
                .linearDamping(linearDamping)
                .angularDamping(angularDamping)
                .apply {
                    when (sleeping) {
                        is Sleeping.Disabled -> canSleep(false)
                        is Sleeping.Enabled -> {
                            canSleep(true)
                            sleeping(sleeping.sleeping)
                        }
                    }
                }
                .use { it.build() }
        }
        return RapierRigidBody.Own(body)
    }

    private fun createJoint(axes: JointAxes): RapierJoint {
        val joint = GenericJoint.create(0).apply {
            var lockedAxes = 0
            axes.forEach { (axis, state) ->
                val rAxis = axis.convert()

                when (state) {
                    is AxisState.Locked -> {
                        lockedAxes = lockedAxes or rAxis.ordinal
                    }
                    is AxisState.Limited -> {
                        setLimits(rAxis, state.min, state.max)
                    }
                    is AxisState.Free -> {}
                }
            }
            lockAxes(lockedAxes.toByte())
        }
        return RapierJoint(RapierJoint.State.Removed(joint))
    }

    override fun createImpulseJoint(axes: JointAxes): ImpulseJoint {
        return createJoint(axes)
    }

    override fun createMultibodyJoint(axes: JointAxes): MultibodyJoint {
        return createJoint(axes)
    }

    override fun createSpace(settings: PhysicsSpace.Settings): PhysicsSpace {
        return RapierSpace(this, settings)
    }

    override fun stepSpaces(dt: Real, spaces: Collection<PhysicsSpace>) {
        @Suppress("UNCHECKED_CAST")
        spaces as Collection<RapierSpace>
        val integrationParameters = spaces.map { space ->
            space.createIntegrationParametersDesc().apply {
                this.dt = dt
                minCcdDt = dt * settings.integration.minCcdDtMultiplier
            }.build()
        }
        PhysicsPipeline.stepAll(
            spaces.map { it.pipeline }.toTypedArray(),
            spaces.map { it.gravity }.toTypedArray(),
            integrationParameters.toTypedArray(),
            spaces.map { it.islands }.toTypedArray(),
            spaces.map { it.broadPhase }.toTypedArray(),
            spaces.map { it.narrowPhase }.toTypedArray(),
            spaces.map { it.rigidBodySet }.toTypedArray(),
            spaces.map { it.colliderSet }.toTypedArray(),
            spaces.map { it.impulseJointSet }.toTypedArray(),
            spaces.map { it.multibodyJointSet }.toTypedArray(),
            spaces.map { it.ccdSolver }.toTypedArray(),
            spaces.map { it.queryPipeline }.toTypedArray(),
        )
        integrationParameters.forEach { it.drop() }
    }
}
