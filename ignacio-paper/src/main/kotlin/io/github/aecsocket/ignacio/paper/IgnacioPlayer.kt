package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.glossa.component
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

class IgnacioPlayer internal constructor(
    private val ignacio: Ignacio,
    val player: Player,
) {
    data class DebugFlags(
        val showTimings: Boolean = false,
    )

    private fun createMessages() = ignacio.messages.forLocale(player.locale())

    private var messages = createMessages()
    var debugFlags = DebugFlags()
        private set
    var timingsBar: BossBar? = null
        private set

    internal fun updateMessages() {
        messages = createMessages()
    }

    fun updateDebugFlags(newFlags: DebugFlags) {
        if (newFlags.showTimings && !debugFlags.showTimings) {
            val bar = ignacio.settings.timingsBar.create(Component.empty())
            player.showBossBar(bar)
            timingsBar = bar
        } else {
            timingsBar?.let { bar ->
                player.hideBossBar(bar)
                timingsBar = null
            }
        }
        debugFlags = newFlags
    }

    internal fun syncUpdate() {
        val world = player.world
        timingsBar?.let { timingsBar ->
            val text = ignacio.worlds[world]?.let { (physics) ->
                val (median, best5, worst5) = timingStatsOf(
                    ignacio.engineTimings.getLast((ignacio.settings.engineTimings.barBuffer * 1000).toLong())
                )
                messages.timingsBar.some(
                    worldName = world.name,
                    numBodies = physics.bodies.count,
                    numActiveBodies = physics.bodies.activeCount,
                    median = formatTiming(median, messages),
                    best5 = formatTiming(best5, messages),
                    worst5 = formatTiming(worst5, messages),
                )
            } ?: messages.timingsBar.none(
                worldName = world.name
            )
            timingsBar.name(text.component())
        }
    }
}