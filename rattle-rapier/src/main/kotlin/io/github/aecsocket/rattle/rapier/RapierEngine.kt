package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.rattle.*
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import rapier.Rapier
import rapier.dynamics.RigidBodyBuilder
import rapier.geometry.ColliderBuilder
import rapier.pipeline.PhysicsPipeline
import rapier.shape.CompoundChild
import rapier.shape.SharedShape

class RapierEngine internal constructor(var settings: Settings = Settings()) : PhysicsEngine {
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
        val handle: SharedShape = pushArena { arena ->
            when (geom) {
                is Sphere -> SharedShape.ball(geom.radius)
                is Box -> if (geom.margin > 0.0) {
                    SharedShape.roundCuboid(geom.halfExtent.x, geom.halfExtent.y, geom.halfExtent.z, geom.margin)
                } else {
                    SharedShape.cuboid(geom.halfExtent.x, geom.halfExtent.y, geom.halfExtent.z)
                }
                is Capsule -> when (geom.axis) {
                    LinAxis.X -> SharedShape.capsule(
                        Vec(-geom.halfHeight, 0.0, 0.0).toVector(arena),
                        Vec( geom.halfHeight, 0.0, 0.0).toVector(arena),
                        geom.radius,
                    )
                    LinAxis.Y -> SharedShape.capsule(
                        Vec(0.0, -geom.halfHeight, 0.0).toVector(arena),
                        Vec(0.0,  geom.halfHeight, 0.0).toVector(arena),
                        geom.radius,
                    )
                    LinAxis.Z -> SharedShape.capsule(
                        Vec(0.0, 0.0, -geom.halfHeight).toVector(arena),
                        Vec(0.0, 0.0,  geom.halfHeight).toVector(arena),
                        geom.radius,
                    )
                }
                is Cylinder -> if (geom.margin > 0.0) {
                    SharedShape.roundCylinder(geom.halfHeight, geom.radius, geom.margin)
                } else {
                    SharedShape.cylinder(geom.halfHeight, geom.radius)
                }
                is Cone -> if (geom.margin > 0.0) {
                    SharedShape.roundCone(geom.halfHeight, geom.radius, geom.margin)
                } else {
                    SharedShape.cone(geom.halfHeight, geom.radius)
                }
                is ConvexHull -> {
                    val points = geom.points.map { it.toVector(arena) }.toTypedArray()
                    val shape = if (geom.margin > 0.0) {
                        SharedShape.roundConvexHull(points, geom.margin)
                    } else {
                        SharedShape.convexHull(*points)
                    }
                    shape ?: throw IllegalArgumentException("Could not create convex hull")
                }
                is ConvexMesh -> {
                    val vertices = geom.vertices.map { it.toVector(arena) }.toTypedArray()
                    val indices = geom.indices.map { intArrayOf(it.x, it.y, it.z) }.toTypedArray()
                    val shape = if (geom.margin > 0.0) {
                        SharedShape.roundConvexMesh(vertices, indices, geom.margin)
                    } else {
                        SharedShape.convexMesh(vertices, indices)
                    }
                    shape ?: throw IllegalArgumentException("Could not create convex mesh")
                }
                is ConvexDecomposition -> {
                    val vertices = geom.vertices.map { it.toVector(arena) }.toTypedArray()
                    val indices = geom.indices.map { intArrayOf(it.x, it.y, it.z) }.toTypedArray()
                    if (geom.margin > 0.0) SharedShape.roundConvexDecomposition(
                        vertices,
                        indices,
                        geom.vhacd.toParams(arena),
                        geom.margin,
                    ) else SharedShape.convexDecomposition(
                        vertices,
                        indices,
                        geom.vhacd.toParams(arena),
                    )
                }
                is Compound -> SharedShape.compound(
                    *geom.children.map { child ->
                        val shape = child.shape as RapierShape
                        CompoundChild.of(arena, child.delta.toIsometry(arena), shape.handle)
                    }.toTypedArray()
                )
            }
        }
        return RapierShape(handle)
    }

    override fun createCollider(shape: Shape): Collider.Own {
        shape as RapierShape
        val coll = ColliderBuilder.of(shape.handle).use { it.build() }
        return RapierCollider.Own(coll)
    }

    override fun createBody(type: RigidBodyType, position: Iso): RigidBody.Own {
        val body = pushArena { arena ->
            RigidBodyBuilder.of(type.convert())
                .position(position.toIsometry(arena))
                .use { it.build() }
        }
        return RapierRigidBody.Own(body)
    }

    data class JointAxis(
        val state: State,
        val motor: Motor,
    ) {
        sealed interface State {
            /* TODO: Kotlin 1.9 data */ object Locked : State

            data class Limited(
                val min: Real = Real.MIN_VALUE,
                val max: Real = Real.MAX_VALUE,
                val impulse: Real = 0.0,
            ) : State

            /* TODO: Kotlin 1.9 data */ object Free : State
        }

        sealed interface Motor {

        }
    }

    fun createJoint(
        localFrameA: Iso = Iso(),
        localFrameB: Iso = Iso(),
        contactsEnabled: Boolean = true,
    ): Joint {
        TODO()
    }

    override fun createSpace(
        settings: PhysicsSpace.Settings,
    ): PhysicsSpace {
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

    class Builder(
        private val settings: Settings = Settings(),
    ) : PhysicsEngine.Builder {
        private var nextLayer = 0

        override fun registerInteractionLayer(): InteractionLayer {
            if (nextLayer >= 32)
                throw IllegalStateException("Cannot register more than 32 interaction layers")
            return InteractionLayer.fromRaw(1 shl nextLayer)
                .also { nextLayer += 1 }
        }

        override fun build() = RapierEngine(settings)
    }
}
