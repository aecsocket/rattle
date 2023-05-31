package io.github.aecsocket.rattle.paper

import io.github.aecsocket.rattle.AbstractPrimitiveBodies
import io.github.aecsocket.rattle.PrimitiveBodies
import org.bukkit.World

class PaperPrimitiveBodies(
    rattle: PaperRattle,
) : AbstractPrimitiveBodies<World>(rattle.rattleServer) {
}
