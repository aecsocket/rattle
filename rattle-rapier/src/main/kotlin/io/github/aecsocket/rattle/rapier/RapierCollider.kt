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

data class RapierColliderHandle(val id: Long) : ColliderHandle {
    override fun toString(): String = ArenaKey.asString(id)
}

object RapierCollider {
    sealed class Base(
        override val handle: rapier.geometry.Collider,
        var space: RapierSpace? = null,
    ) : RapierNative(), Collider.Read {
        override val shape: Shape
            get() = RapierShape(handle.shape)

        override val material: PhysicsMaterial
            get() = PhysicsMaterial(
                friction = handle.friction,
                restitution = handle.restitution,
                frictionCombine = handle.frictionCombineRule.convert(),
                restitutionCombine = handle.restitutionCombineRule.convert(),
            )

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

        override val parent: RigidBodyHandle?
            get() = handle.parent?.let { RapierRigidBodyHandle(it) }

        override fun bounds(): Aabb {
            return pushArena { arena ->
                handle.computeAabb(arena).toAabb()
            }
        }
    }

    class Read internal constructor(
        handle: rapier.geometry.Collider,
        space: RapierSpace? = null,
    ) : Base(handle, space) {
        override val nativeType get() = "RapierCollider.Read"
    }

    open class Write internal constructor(
        override val handle: rapier.geometry.Collider.Mut,
        space: RapierSpace? = null,
    ) : Base(handle, space), Collider.Write {
        override val nativeType get() = "RapierCollider.Write"

        override var shape: Shape
            get() = super.shape
            set(value) {
                value as RapierShape
                handle.shape = value.handle
            }

        override var material: PhysicsMaterial
            get() = super.material
            set(value) {
                handle.friction = value.friction
                handle.restitution = value.restitution
                handle.frictionCombineRule = value.frictionCombine.convert()
                handle.restitutionCombineRule = value.restitutionCombine.convert()
            }

        override var position: Iso
            get() = super.position
            set(value) = pushArena { arena ->
                handle.setPosition(value.toIsometry(arena))
            }

        override var physicsMode: PhysicsMode
            get() = super.physicsMode
            set(value) {
                handle.isSensor = when (value) {
                    PhysicsMode.SOLID -> false
                    PhysicsMode.SENSOR -> true
                }
            }

        override var relativePosition: Iso
            get() = super.relativePosition
            set(value) = pushArena { arena ->
                handle.setPositionWrtParent(value.toIsometry(arena))
            }
    }

    class Own internal constructor(
        handle: rapier.geometry.Collider.Mut,
        space: RapierSpace? = null,
    ) : Write(handle, space), Collider.Own {
        override val nativeType get() = "RapierCollider.Own"

        private val destroyed = DestroyFlag()

        override fun destroy() {
            destroyed()
            space?.let { space ->
                throw IllegalStateException("Attempting to remove $this while still attached to $space")
            }
            handle.drop()
        }
    }
}
