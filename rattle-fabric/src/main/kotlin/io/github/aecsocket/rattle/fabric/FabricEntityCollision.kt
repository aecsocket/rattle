package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.rattle.PhysicsSpace
import io.github.aecsocket.rattle.world.EntityCollision

class FabricEntityCollision(
    override val platform: FabricRattlePlatform,
    physics: PhysicsSpace,
    settings: Settings = Settings(),
) : EntityCollision(platform, physics, settings) {
  fun onTick() {}
}
