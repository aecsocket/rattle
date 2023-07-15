package io.github.aecsocket.rattle.paper

import io.github.aecsocket.rattle.ColliderKey
import io.github.aecsocket.rattle.PhysicsSpace
import io.github.aecsocket.rattle.RigidBodyKey
import io.github.aecsocket.rattle.world.EntityCollision
import java.util.UUID
import org.bukkit.entity.Entity

class PaperEntityCollision(
    override val platform: PaperRattlePlatform,
    // SAFETY: while a caller has access to this object, they also have access to the containing
    // WorldPhysics, and therefore the PhysicsSpace is locked
    physics: PhysicsSpace,
    settings: Settings = Settings(),
) : EntityCollision(platform, physics, settings) {
  private data class Instance(
      val body: RigidBodyKey,
      val collider: ColliderKey,
  )

  private val entityToInst = HashMap<UUID, Instance>()

  internal fun onAdd(entity: Entity) {
    val entityId = entity.uniqueId
    if (entityToInst.contains(entityId)) return
    platform.plugin.scheduling
        .onEntity(
            entity = entity,
            onRetire = {
              val inst = entityToInst.remove(entityId) ?: return@onEntity

              platform.rattle.runTask {}
            },
        )
        .runRepeating {}
  }

  internal fun onRemove(entity: Entity) {}
}
