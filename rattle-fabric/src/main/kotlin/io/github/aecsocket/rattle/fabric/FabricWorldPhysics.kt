package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.rattle.DestroyFlag
import io.github.aecsocket.rattle.world.EntityStrategy
import io.github.aecsocket.rattle.PhysicsSpace
import io.github.aecsocket.rattle.world.TerrainStrategy
import io.github.aecsocket.rattle.world.WorldPhysics
import net.minecraft.server.level.ServerLevel

class FabricWorldPhysics(
    override val world: ServerLevel,
    override val physics: PhysicsSpace,
    override val terrain: TerrainStrategy,
    override val entities: EntityStrategy,
) : WorldPhysics<ServerLevel> {
    private val destroyed = DestroyFlag()

    internal fun destroy() {
        destroyed()
        world as LevelPhysicsAccess
        world.rattle_setPhysics(null)
        physics.destroy()
    }

    fun onTick() {

    }

    override fun onPhysicsStep() {
        terrain.onPhysicsStep()
        entities.onPhysicsStep()
    }
}
