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
    override val simpleBodies: FabricSimpleBodies,
) : WorldPhysics<ServerLevel> {
    private val destroyed = DestroyFlag()

    override fun destroy() {
        destroyed()
        (world as LevelPhysicsAccess).rattle_setPhysics(null)
        terrain.destroy()
        entities.destroy()
        simpleBodies.destroy()
        physics.destroy()
    }

    override fun onPhysicsStep() {
        terrain.onPhysicsStep()
        entities.onPhysicsStep()
        simpleBodies.onPhysicsStep()
    }

    fun onTick() {
        simpleBodies.onTick()
    }
}
