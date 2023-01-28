package io.gitlab.aecsocket.ignacio.paper

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams
import net.kyori.adventure.text.Component
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.WorldLoadEvent
import org.bukkit.event.world.WorldUnloadEvent

internal class IgnacioEventListener(private val ignacio: Ignacio) : Listener {
    @EventHandler
    fun on(event: PlayerJoinEvent) {
        val player = event.player
        IgnacioColorTeams.ColorToTeam.forEach { (color, teamName ) ->
            player.sendPacket(WrapperPlayServerTeams(
                teamName,
                WrapperPlayServerTeams.TeamMode.CREATE,
                WrapperPlayServerTeams.ScoreBoardTeamInfo(
                    Component.empty(), null, null,
                    WrapperPlayServerTeams.NameTagVisibility.ALWAYS, WrapperPlayServerTeams.CollisionRule.ALWAYS,
                    color, WrapperPlayServerTeams.OptionData.NONE
                )
            ))
        }
    }

    @EventHandler
    fun on(event: PlayerQuitEvent) {
        ignacio.playerData.remove(event.player)
    }

    @EventHandler
    fun on(event: WorldLoadEvent) {
        ignacio.physicsSpaceOf(event.world)
    }

    @EventHandler
    fun on(event: WorldUnloadEvent) {
        ignacio.removeSpace(event.world)
    }
}
