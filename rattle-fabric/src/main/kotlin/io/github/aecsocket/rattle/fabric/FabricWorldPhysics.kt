package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.rattle.PhysicsSpace
import io.github.aecsocket.rattle.world.WorldPhysics
import net.minecraft.server.level.ServerLevel

class FabricWorldPhysics(
    world: ServerLevel,
    physics: PhysicsSpace,
    override val terrain: FabricDynamicTerrain?,
    override val entities: FabricEntityStrategy?,
    override val simpleBodies: FabricSimpleBodies,
) : WorldPhysics<ServerLevel>(world, physics, terrain, entities, simpleBodies) {
    override fun destroyInternal() {
        (world as LevelPhysicsAccess).rattle_setPhysics(null)
    }

    fun onTick() {
        simpleBodies.onTick()
        terrain?.onTick()
        entities?.onTick()
    }
}
