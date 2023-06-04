package io.github.aecsocket.rattle.paper

import io.github.aecsocket.rattle.world.EntityStrategy
import io.github.aecsocket.rattle.PhysicsSpace
import io.github.aecsocket.rattle.world.TerrainStrategy
import io.github.aecsocket.rattle.world.WorldPhysics
import org.bukkit.World

class PaperWorldPhysics internal constructor(
    private val rattle: PaperRattle,
    world: World,
    physics: PhysicsSpace,
    terrain: TerrainStrategy,
    entities: EntityStrategy,
    simpleBodies: PaperSimpleBodies,
) : WorldPhysics<World>(world, physics, terrain, entities, simpleBodies) {
    override fun destroyInternal() {
        rattle.mWorlds.remove(world)
    }
}
