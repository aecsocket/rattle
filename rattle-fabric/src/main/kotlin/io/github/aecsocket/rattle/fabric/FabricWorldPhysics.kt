package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.rattle.EntityStrategy
import io.github.aecsocket.rattle.PhysicsSpace
import io.github.aecsocket.rattle.TerrainStrategy
import io.github.aecsocket.rattle.WorldPhysics
import net.minecraft.server.level.ServerLevel

class FabricWorldPhysics(
    override val world: ServerLevel,
    override val physics: PhysicsSpace,
    override val terrain: TerrainStrategy,
    override val entities: EntityStrategy,
) : WorldPhysics<ServerLevel> {
    fun onTick() {
        // todo
    }

    override fun destroy() {
        world as LevelPhysicsAccess
        // unassign ourselves from our world
        world.rattle_setPhysics(null)
        physics.destroy()
    }
}
