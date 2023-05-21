package io.github.aecsocket.rattle.paper

import io.github.aecsocket.rattle.PhysicsSpace
import org.bukkit.World

class WorldPhysics(
    val space: PhysicsSpace,
    val world: World,
) {
    operator fun component1() = space
}
