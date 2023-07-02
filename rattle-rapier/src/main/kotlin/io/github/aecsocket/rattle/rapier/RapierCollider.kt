package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.klam.*
import io.github.aecsocket.rattle.*
import rapier.data.ArenaKey
import rapier.geometry.ActiveEvents
import rapier.geometry.ActiveHooks
import rapier.shape.SharedShape

class RapierShape internal constructor(
    override val handle: SharedShape,
) : RapierRefCounted(), Shape {
    override val nativeType get() = "RapierShape"

    override fun acquire() = this.apply { handle.acquire() }

    override fun release() = this.apply { handle.release() }
}

@JvmInline
value class RapierColliderKey(val handle: ArenaKey) : ColliderKey {
    override fun toString() = handle.toString()
}

sealed class RapierCollider(
    override val handle: rapier.geometry.Collider,
    override var space: RapierSpace?,
) : RapierNative(), RapierPhysicsNative, Collider {
    protected fun checkLock() = checkLock("collider", space?.lock)

    override val shape: Shape
        get() {
            checkLock()
            return RapierShape(handle.shape)
        }

    override val material: PhysicsMaterial
        get() {
            checkLock()
            return PhysicsMaterial(
                friction = handle.friction,
                restitution = handle.restitution,
                frictionCombine = handle.frictionCombineRule.toRattle(),
                restitutionCombine = handle.restitutionCombineRule.toRattle(),
            )
        }

    override val collisionGroup: InteractionGroup
        get() {
            checkLock()
            return handle.collisionGroups.toRattle()
        }

    override val solverGroup: InteractionGroup
        get() {
            checkLock()
            return handle.solverGroups.toRattle()
        }

    override val position: DIso3
        get() {
            checkLock()
            return handle.position.toIso()
        }

    override val relativePosition: DIso3
        get() {
            checkLock()
            return handle.positionWrtParent?.toIso() ?: DIso3.identity
        }

    override val mass: Double
        get() {
            checkLock()
            return handle.mass
        }

    override val density: Double
        get() {
            checkLock()
            return handle.density
        }

    override val physicsMode: PhysicsMode
        get() {
            checkLock()
            return when (handle.isSensor) {
                false -> PhysicsMode.SOLID
                true -> PhysicsMode.SENSOR
            }
        }

    override val parent: RigidBodyKey?
        get() {
            checkLock()
            return handle.parent?.let { RapierRigidBodyKey(it) }
        }

    override fun bounds(): DAabb3 {
        checkLock()
        return handle.computeAabb().toAabb()
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
            checkLock()
            destroyed()
            space?.let { space ->
                throw IllegalStateException("Attempting to remove $this while still attached to $space")
            }
            handle.drop()
        }

        override fun shape(value: Shape): Write {
            checkLock()
            value as RapierShape
            handle.shape = value.handle
            return this
        }

        override fun material(value: PhysicsMaterial): Write {
            checkLock()
            handle.friction = value.friction
            handle.restitution = value.restitution
            handle.frictionCombineRule = value.frictionCombine.toRapier()
            handle.restitutionCombineRule = value.restitutionCombine.toRapier()
            return this
        }

        override fun collisionGroup(value: InteractionGroup): Write {
            checkLock()
            handle.collisionGroups = value.toRapier()
            return this
        }

        override fun solverGroup(value: InteractionGroup): Write {
            checkLock()
            handle.solverGroups = value.toRapier()
            return this
        }

        override fun position(value: DIso3): Write {
            checkLock()
            handle.position = value.toIsometry()
            return this
        }

        override fun relativePosition(value: DIso3): Write {
            checkLock()
            handle.setPositionWrtParent(value.toIsometry())
            return this
        }

        override fun mass(value: Collider.Mass): Write {
            checkLock()
            when (value) {
                is Collider.Mass.Constant -> handle.mass = value.mass
                is Collider.Mass.Density -> handle.density = value.density
                is Collider.Mass.Infinite -> handle.mass = 0.0
            }
            return this
        }

        override fun physicsMode(value: PhysicsMode): Write {
            checkLock()
            handle.isSensor = when (value) {
                PhysicsMode.SOLID -> false
                PhysicsMode.SENSOR -> true
            }
            return this
        }

        override fun handlesEvents(value: ColliderEvents): Collider.Own {
            checkLock()
            var events = 0
            var hooks = 0
            value.forEach {
                when (it) {
                    ColliderEvent.COLLISION -> events = events or ActiveEvents.COLLISION_EVENTS
                    ColliderEvent.CONTACT_FORCE -> events = events or ActiveEvents.CONTACT_FORCE_EVENTS

                    ColliderEvent.FILTER_CONTACT_PAIR -> hooks = hooks or ActiveHooks.FILTER_CONTACT_PAIRS
                    ColliderEvent.FILTER_INTERSECTION_PAIR -> hooks = hooks or ActiveHooks.FILTER_INTERSECTION_PAIR
                    ColliderEvent.SOLVER_CONTACT -> hooks = hooks or ActiveHooks.MODIFY_SOLVER_CONTACTS
                    null -> throw IllegalStateException("Null event")
                }
            }
            handle.activeEvents = events
            handle.activeHooks = hooks
            return this
        }
    }
}
