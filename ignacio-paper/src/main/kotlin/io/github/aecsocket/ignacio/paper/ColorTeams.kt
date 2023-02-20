package io.github.aecsocket.ignacio.paper

import net.kyori.adventure.text.format.NamedTextColor

object ColorTeams {
    private val colorToTeam = NamedTextColor.NAMES.keyToValue().map { (key, value) ->
        value to "ig_$key"
    }.associate { it }

    private val teamToColor = colorToTeam.map { (a, b) -> b to a }.associate { it }

    fun colorToTeam(color: NamedTextColor) = colorToTeam[color]
        ?: throw IllegalArgumentException("Invalid color $color")

    fun teamToColor(name: String) = teamToColor[name]
        ?: throw IllegalArgumentException("Invalid color team name '$name'")
}
