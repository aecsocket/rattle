package io.github.aecsocket.rattle.paper

import io.github.aecsocket.rattle.DestroyFlag
import io.github.aecsocket.rattle.world.EntityStrategy
import io.github.aecsocket.rattle.PhysicsSpace
import io.github.aecsocket.rattle.world.TerrainStrategy
import io.github.aecsocket.rattle.world.WorldPhysics
import org.bukkit.World

class PaperWorldPhysics(
    override val world: World,
    override val physics: PhysicsSpace,
    override val terrain: TerrainStrategy,
    override val entities: EntityStrategy,
) : WorldPhysics<World> {
    private val destroyed = DestroyFlag()

    override fun destroy() {
        destroyed()
        physics.destroy()
        terrain.destroy()
        entities.destroy()
    }

    override fun onPhysicsStep() {
        terrain.onPhysicsStep()
        entities.onPhysicsStep()
    }
}
