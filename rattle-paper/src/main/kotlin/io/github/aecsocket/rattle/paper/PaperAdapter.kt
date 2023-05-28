package io.github.aecsocket.rattle.paper

import io.github.aecsocket.alexandria.paper.extension.location
import io.github.aecsocket.alexandria.paper.extension.position
import org.bukkit.Location
import org.bukkit.World

@JvmInline
value class PaperWorld(val handle: World) : io.github.aecsocket.rattle.World {
    override val key get() = handle.key()
}

fun wrap(world: World) = PaperWorld(world)
fun unwrap(world: io.github.aecsocket.rattle.World) = (world as PaperWorld).handle

fun wrap(location: Location) = io.github.aecsocket.rattle.Location(
    world = wrap(location.world),
    position = location.position(),
)
fun unwrap(location: io.github.aecsocket.rattle.Location) = location.position.location(unwrap(location.world))
