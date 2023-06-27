package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.klam.*
import io.github.aecsocket.rattle.*
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import rapier.Rapier
import rapier.dynamics.RigidBodyBuilder
import rapier.dynamics.joint.GenericJoint
import rapier.geometry.ColliderBuilder
import rapier.pipeline.PhysicsPipeline
import rapier.pipeline.QueryPipeline
import rapier.shape.CompoundChild
import rapier.shape.SharedShape
import java.util.concurrent.locks.ReentrantLock

class RapierEngine internal constructor(var settings: Settings = Settings()) : PhysicsEngine {
    @ConfigSerializable
    data class Settings(
        val integration: Integration = Integration(),
    ) {
        @ConfigSerializable
        data class Integration(
            val minCcdDtMultiplier: Double = 0.01,
            val erp: Double = 0.8,
            val dampingRatio: Double = 0.25,
            val jointErp: Double = 1.0,
            val jointDampingRatio: Double = 1.0,
            val allowedLinearError: Double = 0.001,
            val maxPenetrationCorrection: Double = Double.MAX_VALUE,
            val predictionDistance: Double = 0.002,
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
        val handle: SharedShape = when (geom) {
            is Sphere -> SharedShape.ball(geom.radius)
            is Box -> if (geom.margin > 0.0) {
                SharedShape.roundCuboid(geom.halfExtent.x, geom.halfExtent.y, geom.halfExtent.z, geom.margin)
            } else {
                SharedShape.cuboid(geom.halfExtent.x, geom.halfExtent.y, geom.halfExtent.z)
            }
            is Capsule -> when (geom.axis) {
                LinAxis.X -> SharedShape.capsule(
                    DVec3(-geom.halfHeight, 0.0, 0.0).toVector(),
                    DVec3( geom.halfHeight, 0.0, 0.0).toVector(),
                    geom.radius,
                )
                LinAxis.Y -> SharedShape.capsule(
                    DVec3(0.0, -geom.halfHeight, 0.0).toVector(),
                    DVec3(0.0,  geom.halfHeight, 0.0).toVector(),
                    geom.radius,
                )
                LinAxis.Z -> SharedShape.capsule(
                    DVec3(0.0, 0.0, -geom.halfHeight).toVector(),
                    DVec3(0.0, 0.0,  geom.halfHeight).toVector(),
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
                val points = geom.points.map { it.toVector() }.toTypedArray()
                val shape = if (geom.margin > 0.0) {
                    SharedShape.roundConvexHull(points, geom.margin)
                } else {
                    SharedShape.convexHull(*points)
                }
                shape ?: throw IllegalArgumentException("Could not create convex hull")
            }
            is ConvexMesh -> {
                val vertices = geom.vertices.map { it.toVector() }.toTypedArray()
                val indices = geom.indices.map { intArrayOf(it.x, it.y, it.z) }.toTypedArray()
                val shape = if (geom.margin > 0.0) {
                    SharedShape.roundConvexMesh(vertices, indices, geom.margin)
                } else {
                    SharedShape.convexMesh(vertices, indices)
                }
                shape ?: throw IllegalArgumentException("Could not create convex mesh")
            }
            is ConvexDecomposition -> {
                val vertices = geom.vertices.map { it.toVector() }.toTypedArray()
                val indices = geom.indices.map { intArrayOf(it.x, it.y, it.z) }.toTypedArray()
                if (geom.margin > 0.0) SharedShape.roundConvexDecomposition(
                    vertices,
                    indices,
                    geom.vhacd.toParams(),
                    geom.margin,
                ) else SharedShape.convexDecomposition(
                    vertices,
                    indices,
                    geom.vhacd.toParams(),
                )
            }
            is Compound -> SharedShape.compound(
                *geom.children.map { child ->
                    val shape = child.shape as RapierShape
                    CompoundChild(child.delta.toIsometry(), shape.handle)
                }.toTypedArray()
            )
        }
        return RapierShape(handle)
    }

    override fun createCollider(shape: Shape, position: StartPosition): Collider.Own {
        shape as RapierShape
        val coll = ColliderBuilder.of(shape.handle).use { it.build() }
        when (position) {
            is StartPosition.Absolute -> coll.setPosition(position.pos.toIsometry())
            is StartPosition.Relative -> coll.setPositionWrtParent(position.pos.toIsometry())
        }
        return RapierCollider.Write(coll, space = null)
    }

    override fun createBody(type: RigidBodyType, position: DIso3): RigidBody.Own {
        val body = RigidBodyBuilder.of(type.toRapier())
            .position(position.toIsometry())
            .use { it.build() }
        return RapierRigidBody.Write(body, space = null)
    }

    override fun createJoint(): Joint {
        val joint = GenericJoint.of(0.toByte())
        return RapierJoint.Write(joint, space = null)
    }

    override fun createSpace(
        settings: PhysicsSpace.Settings,
    ): PhysicsSpace {
        return RapierSpace(this, settings)
    }

    override fun stepSpaces(dt: Double, spaces: Collection<PhysicsSpace>) {
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
            spaces.map { it.settings.gravity.toVector() }.toTypedArray(),
            integrationParameters.toTypedArray(),
            spaces.map { it.islands }.toTypedArray(),
            spaces.map { it.broadPhase }.toTypedArray(),
            spaces.map { it.narrowPhase }.toTypedArray(),
            spaces.map { it.rigidBodySet }.toTypedArray(),
            spaces.map { it.colliderSet }.toTypedArray(),
            spaces.map { it.impulseJointSet }.toTypedArray(),
            spaces.map { it.multibodyJointSet }.toTypedArray(),
            spaces.map { it.ccdSolver }.toTypedArray(),
            spaces.map { null }.toTypedArray(),
            spaces.map { it.hooks }.toTypedArray(),
            spaces.map { it.events }.toTypedArray(),
        )
        QueryPipeline.updateAll(
            spaces.map { it.queryPipeline }.toTypedArray(),
            spaces.map { it.rigidBodySet }.toTypedArray(),
            spaces.map { it.colliderSet }.toTypedArray(),
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

internal fun checkLock(objType: String, lock: ReentrantLock?) {
    if (lock == null) return
    if (!lock.isHeldByCurrentThread)
        throw IllegalStateException("${Thread.currentThread().name}: Attempting to read/write $objType while not locked by this thread")
}
