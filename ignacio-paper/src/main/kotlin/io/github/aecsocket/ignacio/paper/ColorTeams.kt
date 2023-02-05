package io.github.aecsocket.ignacio.paper

import net.kyori.adventure.text.format.NamedTextColor

object ColorTeams {
    val ColorToTeam = NamedTextColor.NAMES.keyToValue().map { (key, value) ->
        value to "ig_$key"
    }.associate { it }

    val TeamToColor = ColorToTeam.map { (a, b) -> b to a }.associate { it }

    fun colorToTeam(color: NamedTextColor) = ColorToTeam[color]
        ?: throw IllegalArgumentException("Invalid color $color")

    fun teamToColor(name: String) = TeamToColor[name]
        ?: throw IllegalArgumentException("Invalid color team name '$name'")
}
