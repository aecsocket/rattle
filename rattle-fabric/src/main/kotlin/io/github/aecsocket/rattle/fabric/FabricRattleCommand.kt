package io.github.aecsocket.rattle.fabric

import cloud.commandframework.bukkit.parsers.location.LocationArgument
import io.github.aecsocket.alexandria.fabric.commandManager
import io.github.aecsocket.alexandria.paper.commandManager
import io.github.aecsocket.rattle.RattleCommand
import net.minecraft.commands.CommandSourceStack
import org.bukkit.command.CommandSender

internal class FabricRattleCommand(rattle: Rattle) : RattleCommand<CommandSourceStack>(rattle, commandManager()) {
    override fun positionArgumentOf(name: String) =
}