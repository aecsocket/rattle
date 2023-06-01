package io.github.aecsocket.rattle.paper

import io.github.aecsocket.rattle.AbstractSimpleBodies
import org.bukkit.World

class PaperSimpleBodies(
    world: World,
    rattle: PaperRattle,
) : AbstractSimpleBodies<World>(world, rattle.platform) {
}
