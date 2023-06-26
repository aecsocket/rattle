package io.github.aecsocket.rattle.paper

import io.github.aecsocket.rattle.CommandSource
import org.bukkit.World
import org.bukkit.command.CommandSender

internal typealias RWorld = io.github.aecsocket.rattle.World
internal typealias RLocation = io.github.aecsocket.rattle.Location

internal class PaperCommandSource(val handle: CommandSender) : CommandSource

internal fun CommandSender.wrap() = PaperCommandSource(this)
internal fun CommandSource.unwrap() = (this as PaperCommandSource).handle

internal class PaperWorld(val handle: World) : RWorld

internal fun World.wrap() = PaperWorld(this)
internal fun RWorld.unwrap() = (this as PaperWorld).handle
