package io.github.aecsocket.rattle.paper

import cloud.commandframework.bukkit.parsers.WorldArgument
import cloud.commandframework.bukkit.parsers.location.LocationArgument
import cloud.commandframework.context.CommandContext
import io.github.aecsocket.alexandria.paper.commandManager
import io.github.aecsocket.alexandria.paper.extension.location
import io.github.aecsocket.rattle.RattleCommand
import io.github.aecsocket.rattle.RigidBody
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

internal class PaperRattleCommand(
    val rattle: RattlePlugin,
) : RattleCommand<CommandSender>(rattle, commandManager(rattle)) {
    override fun locationArgumentOf(key: String) =
        LocationArgument.of<CommandSender>(key)

    override fun CommandContext<CommandSender>.getLocation(key: String) = wrap(get<Location>(key))

    override fun worldArgumentOf(key: String) =
        WorldArgument.of<CommandSender>(key)

    override fun CommandContext<CommandSender>.getWorld(key: String) = wrap(get<World>(key))

    override fun todoBodyStuff(sender: CommandSender, body: RigidBody) {
        sender as Player
        rattle.scheduling.onEntity(sender).runRepeating {
            val pos = body.readBody { rb ->
                rb.position
            }
            sender.spawnParticle(Particle.END_ROD, pos.location(sender.world), 0)
        }
    }
}