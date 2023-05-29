package io.github.aecsocket.rattle.paper

import cloud.commandframework.bukkit.parsers.WorldArgument
import cloud.commandframework.bukkit.parsers.location.LocationArgument
import cloud.commandframework.context.CommandContext
import io.github.aecsocket.alexandria.paper.commandManager
import io.github.aecsocket.alexandria.paper.extension.position
import io.github.aecsocket.rattle.RattleCommand
import io.github.aecsocket.rattle.RattleServer
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.command.CommandSender

internal class PaperRattleCommand(
    private val rattle: RattlePlugin,
) : RattleCommand<CommandSender, World>(rattle, rattle.messages, commandManager(rattle)) {
    override fun locationArgumentOf(key: String) =
        LocationArgument.of<CommandSender>(key)

    override fun CommandContext<CommandSender>.getLocation(key: String): io.github.aecsocket.rattle.Location<World> {
        val loc = get<Location>(key)
        return io.github.aecsocket.rattle.Location(
            world = loc.world,
            position = loc.position(),
        )
    }

    override fun worldArgumentOf(key: String) =
        WorldArgument.of<CommandSender>(key)

    override fun CommandContext<CommandSender>.getWorld(key: String) = get<World>(key)

    override val CommandContext<CommandSender>.server: RattleServer<World, CommandSender>
        get() = rattle
}
