package io.github.aecsocket.rattle.paper

import io.github.aecsocket.rattle.ColliderKey
import io.github.aecsocket.rattle.RigidBodyKey
import io.github.aecsocket.rattle.world.EntityStrategy
import org.bukkit.entity.Entity

class PaperEntityStrategy : EntityStrategy() {
  private data class Instance(
      val body: RigidBodyKey,
      val collider: ColliderKey,
  )

  private val entityToInst = HashMap<Entity, Instance>()
}
