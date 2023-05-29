package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.rattle.EntityStrategy
import io.github.aecsocket.rattle.PhysicsSpace
import io.github.aecsocket.rattle.TerrainStrategy
import io.github.aecsocket.rattle.WorldPhysics
import net.minecraft.world.level.Level

class FabricWorldPhysics(
    override val world: Level,
    override val physics: PhysicsSpace,
    override val terrain: TerrainStrategy,
    override val entities: EntityStrategy,
) : WorldPhysics<Level> {
    fun tick() {
        // todo
    }

    override fun destroy() {
        physics.destroy()
    }
}
