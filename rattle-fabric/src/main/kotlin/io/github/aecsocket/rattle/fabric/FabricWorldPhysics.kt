package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.rattle.EntityStrategy
import io.github.aecsocket.rattle.PhysicsSpace
import io.github.aecsocket.rattle.TerrainStrategy
import io.github.aecsocket.rattle.WorldPhysics
import net.minecraft.server.level.ServerLevel

class FabricWorldPhysics(
    override val physics: PhysicsSpace,
    override val world: ServerLevel,
    override val terrain: TerrainStrategy,
    override val entities: EntityStrategy,
) : WorldPhysics<ServerLevel>
