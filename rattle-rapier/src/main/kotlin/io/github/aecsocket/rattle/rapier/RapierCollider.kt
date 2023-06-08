package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.rattle.*
import rapier.data.ArenaKey
import rapier.shape.SharedShape

class RapierShape internal constructor(
    override val handle: SharedShape,
) : RapierRefCounted(), Shape {
    override val nativeType get() = "RapierShape"

    override fun acquire() = this.apply { handle.acquire() }

    override fun release() = this.apply { handle.release() }
}

@JvmInline
value class RapierColliderKey(val id: Long) : ColliderKey {
    override fun toString(): String = ArenaKey.asString(id)
}

sealed class RapierCollider(
    override val handle: rapier.geometry.Collider,
    override var space: RapierSpace?,
) : RapierNative(), RapierPhysicsNative, Collider {
    override val shape: Shape
        get() = RapierShape(handle.shape)

    override val material: PhysicsMaterial
        get() = PhysicsMaterial(
            friction = handle.friction,
            restitution = handle.restitution,
            frictionCombine = handle.frictionCombineRule.convert(),
            restitutionCombine = handle.restitutionCombineRule.convert(),
        )

    override val collisionGroup: InteractionGroup
        get() = pushArena { arena ->
            handle.getCollisionGroups(arena).convert()
        }

    override val solverGroup: InteractionGroup
        get() = pushArena { arena ->
            handle.getSolverGroups(arena).convert()
        }

    override val position: Iso
        get() = pushArena { arena ->
            handle.getPosition(arena).toIso()
        }

    override val physicsMode: PhysicsMode
        get() = when (handle.isSensor) {
            false -> PhysicsMode.SOLID
            true -> PhysicsMode.SENSOR
        }

    override val relativePosition: Iso
        get() = pushArena { arena ->
            handle.getPositionWrtParent(arena)?.toIso() ?: Iso()
        }

    override val parent: RigidBodyKey?
        get() = handle.parent?.let { RapierRigidBodyKey(it) }

    override fun bounds(): Aabb {
        return pushArena { arena ->
            handle.computeAabb(arena).toAabb()
        }
    }

    class Read internal constructor(
        handle: rapier.geometry.Collider,
        space: RapierSpace?,
    ) : RapierCollider(handle, space) {
        override val nativeType get() = "RapierCollider.Read"
    }

    class Write internal constructor(
        override val handle: rapier.geometry.Collider.Mut,
        space: RapierSpace?,
    ) : RapierCollider(handle, space), Collider.Own {
        override val nativeType get() = "RapierCollider.Write"

        private val destroyed = DestroyFlag()

        override fun destroy() {
            destroyed()
            space?.let { space ->
                throw IllegalStateException("Attempting to remove $this while still attached to $space")
            }
            handle.drop()
        }

        override fun shape(value: Shape): Write {
            value as RapierShape
            handle.shape = value.handle
            return this
        }

        override fun material(value: PhysicsMaterial): Write {
            handle.friction = value.friction
            handle.restitution = value.restitution
            handle.frictionCombineRule = value.frictionCombine.convert()
            handle.restitutionCombineRule = value.restitutionCombine.convert()
            return this
        }

        override fun collisionGroup(value: InteractionGroup): Write {
            pushArena { arena ->
                handle.setCollisionGroups(value.convert(arena))
            }
            return this
        }

        override fun solverGroup(value: InteractionGroup): Write {
            pushArena { arena ->
                handle.setSolverGroups(value.convert(arena))
            }
            return this
        }

        override fun position(value: Iso): Write {
            pushArena { arena ->
                handle.setPosition(value.toIsometry(arena))
            }
            return this
        }

        override fun mass(value: Mass): Write {
            when (value) {
                is Mass.Constant -> handle.mass = value.mass
                is Mass.Density -> handle.density = value.density
                is Mass.Infinite -> handle.mass = 0.0
            }
            return this
        }

        override fun physicsMode(value: PhysicsMode): Write {
            handle.isSensor = when (value) {
                PhysicsMode.SOLID -> false
                PhysicsMode.SENSOR -> true
            }
            return this
        }

        override fun relativePosition(value: Iso): Write {
            pushArena { arena ->
                handle.setPositionWrtParent(value.toIsometry(arena))
            }
            return this
        }
    }
}
