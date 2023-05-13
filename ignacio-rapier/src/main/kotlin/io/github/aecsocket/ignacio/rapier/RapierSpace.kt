package io.github.aecsocket.ignacio.rapier

import io.github.aecsocket.ignacio.*
import rapier.dynamics.*
import rapier.geometry.BroadPhase
import rapier.geometry.ColliderBuilder
import rapier.geometry.ColliderSet
import rapier.geometry.NarrowPhase
import rapier.pipeline.PhysicsPipeline
import rapier.pipeline.QueryPipeline

class RapierSpace internal constructor(
    override var settings: PhysicsSpace.Settings,
) : PhysicsSpace {
    private val destroyed = DestroyFlag()

    val pipeline = PhysicsPipeline.create()
    val islands = IslandManager.create()
    val broadPhase = BroadPhase.create()
    val narrowPhase = NarrowPhase.create()
    val rigidBodySet = RigidBodySet.create()
    val colliderSet = ColliderSet.create()
    val impulseJointSet = ImpulseJointSet.create()
    val multibodyJointSet = MultibodyJointSet.create()
    val ccdSolver = CCDSolver.create()
    val queryPipeline = QueryPipeline.create()

    override fun destroy() {
        destroyed()
        pipeline.drop()
        islands.drop()
        broadPhase.drop()
        narrowPhase.drop()
        rigidBodySet.drop()
        colliderSet.drop()
        impulseJointSet.drop()
        multibodyJointSet.drop()
        ccdSolver.drop()
        queryPipeline.drop()
    }

    override fun step(dt: Real) {
        val integrationParameters = IntegrationParameters.ofDefault()
        integrationParameters.dt = dt
        pushArena { arena ->
            pipeline.step(
                settings.gravity.asVector(arena),
                integrationParameters,
                islands,
                broadPhase,
                narrowPhase,
                rigidBodySet,
                colliderSet,
                impulseJointSet,
                multibodyJointSet,
                ccdSolver,
                queryPipeline,
            )
        }
        integrationParameters.drop()
    }

    override val colliders = object : PhysicsSpace.Colliders {
        override fun create(desc: ColliderDesc): Collider {
            val collider = pushArena { arena ->
                val builder = ColliderBuilder.of((desc.shape as RapierShape).shape)
                    .position(desc.position.asIsometry(arena))
                    .friction(desc.friction)
                    .restitution(desc.restitution)
                builder.build().also { builder.drop() }
            }
            val handle = colliderSet.insert(collider)
            return RapierCollider(this@RapierSpace, ColliderHandle(handle))
        }
    }

    override val rigidBodies = object : PhysicsSpace.RigidBodies {
        fun create(builder: RigidBodyBuilder): RapierRigidBody {
            val body = builder.build().also { builder.drop() }
            val handle = rigidBodySet.insert(body)
            return RapierRigidBody(this@RapierSpace, RigidBodyHandle(handle))
        }

        override fun createFixed(desc: FixedBodyDesc): FixedBody {
            return pushArena { arena ->
                create(RigidBodyBuilder.fixed()
                    .position(desc.position.asIsometry(arena)))
            }
        }

        override fun createMoving(desc: MovingBodyDesc): MovingBody {
            return pushArena { arena ->
                create(RigidBodyBuilder.of(when (desc.isKinematic) {
                    true -> RigidBodyType.KINEMATIC_POSITION_BASED
                    false -> RigidBodyType.DYNAMIC
                })
                    .position(desc.position.asIsometry(arena))
                    .linvel(desc.linearVelocity.asVector(arena))
                    .angvel(desc.angularVelocity.asAngVector(arena))
                    .gravityScale(desc.gravityScale)
                    .linearDamping(desc.linearDamping)
                    .angularDamping(desc.angularDamping)
                    .ccdEnabled(desc.isCcdEnabled)
                    .canSleep(desc.canSleep)
                    .sleeping(desc.isSleeping)
                )
            }
        }
    }
}
