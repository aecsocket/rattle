package io.github.aecsocket.ignacio.paper

import cloud.commandframework.arguments.standard.DoubleArgument
import cloud.commandframework.arguments.standard.FloatArgument
import cloud.commandframework.arguments.standard.IntegerArgument
import cloud.commandframework.bukkit.parsers.WorldArgument
import cloud.commandframework.bukkit.parsers.location.LocationArgument
import io.github.aecsocket.alexandria.extension.flag
import io.github.aecsocket.alexandria.extension.hasFlag
import io.github.aecsocket.alexandria.paper.BaseCommand
import io.github.aecsocket.alexandria.paper.Context
import io.github.aecsocket.alexandria.paper.ItemDescriptor
import io.github.aecsocket.alexandria.paper.extension.position
import io.github.aecsocket.alexandria.paper.render.ModelDescriptor
import io.github.aecsocket.glossa.messageProxy
import io.github.aecsocket.ignacio.*
import io.github.aecsocket.klam.*
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import kotlin.random.Random

private const val COUNT = "count"
private const val DENSITY = "density"
private const val FRICTION = "friction"
private const val HALF_EXTENT = "half-extent"
private const val LOCATION = "location"
private const val MASS = "mass"
private const val RADIUS = "radius"
private const val RESTITUTION = "restitution"
private const val SHOW_TIMINGS = "show-timings"
private const val SPREAD = "spread"
private const val VIRTUAL = "virtual"
private const val WORLD = "world"

