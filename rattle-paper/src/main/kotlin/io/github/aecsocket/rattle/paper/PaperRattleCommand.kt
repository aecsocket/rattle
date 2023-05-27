package io.github.aecsocket.rattle.paper

import cloud.commandframework.bukkit.parsers.location.LocationArgument
import io.github.aecsocket.alexandria.paper.commandManager
import io.github.aecsocket.rattle.RattleCommand
import org.bukkit.command.CommandSender

internal class PaperRattleCommand(rattle: RattlePlugin) : RattleCommand<CommandSender>(rattle, commandManager(rattle)) {
    override fun positionArgumentOf(name: String) = LocationArgument.of<CommandSender>(name)
}