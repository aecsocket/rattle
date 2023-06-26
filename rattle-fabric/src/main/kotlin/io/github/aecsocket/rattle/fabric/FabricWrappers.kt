package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.rattle.CommandSource
import io.github.aecsocket.rattle.World
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerLevel

internal class FabricCommandSource(val handle: CommandSourceStack) : CommandSource

internal fun CommandSourceStack.wrap() = FabricCommandSource(this)
internal fun CommandSource.unwrap() = (this as FabricCommandSource).handle

internal class FabricWorld(val handle: ServerLevel) : World {
    override fun toString() = handle.dimension().key().asString()
}

internal fun ServerLevel.wrap() = FabricWorld(this)
internal fun World.unwrap() = (this as FabricWorld).handle
