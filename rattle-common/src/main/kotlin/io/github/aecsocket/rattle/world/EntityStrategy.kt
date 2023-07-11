package io.github.aecsocket.rattle.world

import io.github.aecsocket.rattle.Destroyable

abstract class EntityStrategy : Destroyable {
  override fun destroy() {}

  fun onPhysicsStep() {}
}
