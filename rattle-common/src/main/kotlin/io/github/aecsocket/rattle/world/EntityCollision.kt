package io.github.aecsocket.rattle.world

import io.github.aecsocket.rattle.Destroyable
import io.github.aecsocket.rattle.PhysicsSpace
import io.github.aecsocket.rattle.impl.RattlePlatform
import org.spongepowered.configurate.objectmapping.ConfigSerializable

abstract class EntityCollision(
    open val platform: RattlePlatform,
    val physics: PhysicsSpace,
    val settings: Settings,
) : Destroyable {
  @ConfigSerializable
  data class Settings(
      val enabled: Boolean = true,
  )

  override fun destroy() {}

  fun onPhysicsStep() {}
}
