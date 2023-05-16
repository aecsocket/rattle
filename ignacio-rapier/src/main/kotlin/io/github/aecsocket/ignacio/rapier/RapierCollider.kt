package io.github.aecsocket.ignacio.rapier

import io.github.aecsocket.ignacio.*
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

    override fun destroy() {
        release()
    }

    override fun toString() = "RapierShape[0x%x]".format(handle.memory().address())
}

class RapierMaterial(
    override val friction: Real,
    override val restitution: Real,
    override val frictionCombine: CoeffCombineRule,
    override val restitutionCombine: CoeffCombineRule,
) : PhysicsMaterial {
    override fun destroy() {}
}

@JvmInline
value class ColliderHandle(val key: ArenaKey)

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
        destroyed()
        val coll = when (val state = state) {
            is State.Removed -> state.coll
            is State.Added -> state.space.colliderSet.remove(
                state.handle.key.id,
                state.space.islands,
                state.space.rigidBodySet,
                false,
            )
        } ?: return
        coll.drop()
    }

    override fun <R> read(block: (Collider.Read) -> R): R {
        return when (val state = state) {
            is State.Removed -> block(Read(state.coll))
            is State.Added -> block(Read(state.space.colliderSet.index(state.handle.key.id)))
        }
    }

    override fun <R> write(block: (Collider.Write) -> R): R {
        return when (val state = state) {
            is State.Removed -> block(Write(state.coll))
            is State.Added -> block(Write(state.space.colliderSet.indexMut(state.handle.key.id)))
        }
    }

    override fun addTo(space: PhysicsSpace) {
        space as RapierSpace
        when (val state = state) {
            is State.Added -> throw IllegalStateException("$this is attempting to be added to $space but is already added to ${state.space}")
            is State.Removed -> {
                val handle = space.colliderSet.insert(state.coll)
                this.state = State.Added(space, ColliderHandle(ArenaKey(handle)))
            }
        }
    }

    override fun remove() {
        when (val state = state) {
            is State.Added -> {
                state.space.colliderSet.remove(
                    state.handle.key.id,
                    state.space.islands,
                    state.space.rigidBodySet,
                    false,
                )
            }
            is State.Removed -> throw IllegalStateException("$this is not added to a space")
        }
    }

    override fun toString() = when (val state = state) {
        is State.Added -> "RapierCollider[${state.handle}]"
        is State.Removed -> "RapierCollider[0x%x]".format(state.coll.memory().address())
    }

    private open inner class Access(open val coll: rapier.geometry.Collider) : Collider.Access {
        override val handle get() = this@RapierCollider

        override val shape: Shape
            get() = RapierShape(coll.shape)

        override val material: PhysicsMaterial
            get() = RapierMaterial(
                friction = coll.friction,
                restitution = coll.restitution,
                frictionCombine = coll.frictionCombineRule.convert(),
                restitutionCombine = coll.restitutionCombineRule.convert(),
            )

        override val position: Iso
            get() = pushArena { arena ->
                coll.getPosition(arena).toIso()
            }

        override val isSensor: Boolean
            get() = coll.isSensor

        override val parent: ColliderParent?
            get() = when (val state = state) {
                // our parent is a body with handle `parentKey` in the same space as us
                is State.Added -> coll.parent?.let { parentKey ->
                    object : ColliderParent {
                        override val body = RapierBody(RapierBody.State.Added(state.space, RigidBodyHandle(ArenaKey(parentKey))))
                        override val position get() = pushArena { arena ->
                            (coll.getPositionWrtParent(arena)
                                ?: throw IllegalStateException("Parent for $this has been removed while parent is still being accessed"))
                                .toIso()
                        }
                    }
                }
                // we can't have a parent, we're not in any physics space
                is State.Removed -> null
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
                value as RapierMaterial
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

        override var isSensor: Boolean
            get() = super.isSensor
            set(value) {
                coll.isSensor = value
            }

        override var parent: ColliderParent.Write?
            get() = TODO()
            set(value) = TODO()
    }
}
