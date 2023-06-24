package io.github.aecsocket.rattle.world

import io.github.aecsocket.klam.*
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.impl.Entity

abstract class StandardEntities(
    // SAFETY: we only access the physics while the containing WorldPhysics is locked
    val physics: PhysicsSpace,
) : EntityStrategy {
    data class EntityState(
        val body: RigidBodyKey,
    ) {
        var nextPosition: DIso3? = null
    }

    private val entities = HashMap<Entity, EntityState>()

    fun onTickEntity(entity: Entity) {
        val state = entities[entity] ?: return
        state.nextPosition = entity.position
    }

    fun onPhysicsTick() {
        entities.forEach { (_, state) ->
            val nextPosition = state.nextPosition ?: return@forEach
            val body = physics.rigidBodies.write(state.body) ?: return@forEach
            body.moveTo(nextPosition)
        }
    }
}
