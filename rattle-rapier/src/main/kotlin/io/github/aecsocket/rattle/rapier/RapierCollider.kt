package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.rattle.*
import rapier.data.ArenaKey
import rapier.shape.SharedShape

class RapierShape internal constructor(
    val handle: SharedShape
) : Shape, RefCounted {
    override val refCount: Long
        get() = handle.strongCount()

    override fun acquire(): RapierShape {
        handle.acquire()
        return this
    }

    override fun release(): RapierShape {
        handle.release()
        return this
    }

    override fun toString() = "RapierShape[0x%x]".format(handle.address())
}

@JvmInline
value class ColliderHandle(val id: Long) {
    override fun toString(): String = ArenaKey.asString(id)
}

class RapierCollider internal constructor(
    var state: State,
) : Collider {
    sealed interface State {
        data class Removed(
            val coll: rapier.geometry.Collider.Mut,
        ) : State

        data class Added(
            val space: RapierSpace,
            val handle: ColliderHandle,
        ) : State
    }

    private val destroyed = DestroyFlag()

    override fun destroy() {
        when (val state = state) {
            is State.Removed -> {
                destroyed()
                state.coll.drop()
            }
            is State.Added -> throw IllegalStateException("$this is still added to ${state.space}")
        }
    }

    override fun <R> read(block: (Collider.Read) -> R): R {
        return when (val state = state) {
            is State.Removed -> block(Read(state.coll))
            is State.Added -> block(Read(state.space.colliderSet.get(state.handle.id)
                ?: throw IllegalArgumentException("No collider with ID ${state.handle}")))
        }
    }

    override fun <R> write(block: (Collider.Write) -> R): R {
        return when (val state = state) {
            is State.Removed -> block(Write(state.coll))
            is State.Added -> block(Write(state.space.colliderSet.getMut(state.handle.id)
                ?: throw IllegalArgumentException("No collider with ID ${state.handle}")))
        }
    }

    override fun toString() = when (val state = state) {
        is State.Added -> "RapierCollider[${state.handle}]"
        is State.Removed -> "RapierCollider[0x%x]".format(state.coll.address())
    }

    override fun equals(other: Any?) = other is RapierCollider && state == other.state

    override fun hashCode() = state.hashCode()

    private open inner class Access(open val coll: rapier.geometry.Collider) : Collider.Access {
        override val handle get() = this@RapierCollider

        override val shape: Shape
            get() = RapierShape(coll.shape)

        override val material: PhysicsMaterial
            get() = PhysicsMaterial(
                friction = coll.friction,
                restitution = coll.restitution,
                frictionCombine = coll.frictionCombineRule.convert(),
                restitutionCombine = coll.restitutionCombineRule.convert(),
            )

        override val position: Iso
            get() = pushArena { arena ->
                coll.getPosition(arena).toIso()
            }

        override val physicsMode: PhysicsMode
            get() = when (coll.isSensor) {
                false -> PhysicsMode.SOLID
                true -> PhysicsMode.SENSOR
            }

        override val relativePosition: Iso
            get() = pushArena { arena ->
                coll.getPositionWrtParent(arena)?.toIso() ?: Iso()
            }

        override val parent: RigidBody?
            get() = when (val state = state) {
                is State.Added -> coll.parent?.let { parentKey ->
                    RapierBody(RapierBody.State.Added(
                        space = state.space,
                        handle = RigidBodyHandle(parentKey),
                    ))
                }
                is State.Removed -> null
            }

        override fun bounds(): Aabb {
            return pushArena { arena ->
                coll.computeAabb(arena).toAabb()
            }
        }
    }

    private inner class Read(coll: rapier.geometry.Collider) : Access(coll), Collider.Read

    private inner class Write(override val coll: rapier.geometry.Collider.Mut) : Access(coll), Collider.Write {
        override var shape: Shape
            get() = super.shape
            set(value) {
                value as RapierShape
                coll.shape = value.handle
            }

        override var material: PhysicsMaterial
            get() = super.material
            set(value) {
                coll.friction = value.friction
                coll.restitution = value.restitution
                coll.frictionCombineRule = value.frictionCombine.convert()
                coll.restitutionCombineRule = value.restitutionCombine.convert()
            }

        override var position: Iso
            get() = super.position
            set(value) = pushArena { arena ->
                coll.setPosition(value.toIsometry(arena))
            }

        override var physicsMode: PhysicsMode
            get() = super.physicsMode
            set(value) {
                coll.isSensor = when (value) {
                    PhysicsMode.SOLID -> false
                    PhysicsMode.SENSOR -> true
                }
            }

        override var relativePosition: Iso
            get() = super.relativePosition
            set(value) = pushArena { arena ->
                coll.setPositionWrtParent(value.toIsometry(arena))
            }

        override var parent: RigidBody?
            get() = super.parent
            set(value) = when (val state = state) {
                is State.Added -> {
                    val parentHandle = value?.let { parent ->
                        parent as RapierBody
                        when (val parentState = parent.state) {
                            is RapierBody.State.Added -> {
                                if (state.space != parentState.space)
                                    throw IllegalArgumentException("Attempting to attach $value (in ${parentState.space}) to $handle (in ${state.space})")
                                parentState.handle.id
                            }
                            is RapierBody.State.Removed -> throw IllegalStateException("Attempting to attach $parent, which is not in a space, to $this")
                        }
                    }

                    state.space.colliderSet.setParent(
                        state.handle.id,
                        parentHandle,
                        state.space.rigidBodySet,
                    )
                }
                is State.Removed -> throw IllegalStateException("$this is not added to a space")
            }
    }
}
