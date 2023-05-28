package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.rattle.World
import net.minecraft.world.level.Level

@JvmInline
value class FabricWorld(val handle: Level) : World {
    override val key get() = handle.dimension().key()
}

fun wrap(world: Level) = FabricWorld(world)
fun unwrap(world: World) = (world as FabricWorld).handle
