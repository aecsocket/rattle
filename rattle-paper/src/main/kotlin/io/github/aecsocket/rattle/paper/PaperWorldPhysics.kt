package io.github.aecsocket.rattle.paper

import io.github.aecsocket.rattle.PhysicsSpace
import io.github.aecsocket.rattle.world.WorldPhysics
import org.bukkit.World

class PaperWorldPhysics
internal constructor(
    private val platform: PaperRattlePlatform,
    physics: PhysicsSpace,
    override val terrain: PaperTerrainCollision?,
    override val entities: PaperEntityCollision?,
    simpleBodies: PaperSimpleBodies,
    val world: World,
) : WorldPhysics(physics, terrain, entities, simpleBodies) {
  override fun destroyInternal() {
    platform.removePhysics(world)
  }
}
