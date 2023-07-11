package io.github.aecsocket.rattle.paper

import cloud.commandframework.bukkit.parsers.WorldArgument
import cloud.commandframework.bukkit.parsers.location.LocationArgument
import cloud.commandframework.context.CommandContext
import io.github.aecsocket.alexandria.paper.commandManager
import io.github.aecsocket.alexandria.paper.extension.position
import io.github.aecsocket.rattle.impl.RattleCommand
import io.github.aecsocket.rattle.impl.RattlePlatform
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.command.CommandSender

internal class PaperRattleCommand(
    private val rattle: PaperRattle,
) : RattleCommand<CommandSender>(rattle.rattle, rattle.messages, commandManager(rattle)) {
  override fun locationArgumentOf(key: String) = LocationArgument.of<CommandSender>(key)

  override fun CommandContext<CommandSender>.getLocation(key: String): RLocation {
    val loc = get<Location>(key)
    return RLocation(
        world = loc.world.wrap(),
        position = loc.position(),
    )
  }

  override fun worldArgumentOf(key: String) = WorldArgument.of<CommandSender>(key)

  override fun CommandContext<CommandSender>.getWorld(key: String) = get<World>(key).wrap()

  override val CommandContext<CommandSender>.server: RattlePlatform
    get() = rattle.platform

  override fun CommandSender.source() = wrap()
}
