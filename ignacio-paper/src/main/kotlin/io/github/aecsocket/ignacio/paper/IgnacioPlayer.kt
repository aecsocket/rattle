package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.glossa.core.component
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import java.util.Locale

enum class PlayerDebugFlag {
    SHOW_TIMINGS
}

class IgnacioPlayer internal constructor(
    private val ignacio: Ignacio,
    val player: Player
) {
    private var messages = createMessages(player.locale())
    var debugFlags: Set<PlayerDebugFlag> = HashSet()
        private set
    private var timingsBar: BossBar? = null

    private fun createMessages(locale: Locale) = ignacio.messages.forLocale(locale)

    internal fun updateMessages(locale: Locale) {
        messages = createMessages(locale)
    }

    private fun addTimingsBar() {
        removeTimingsBar()
        val bar = ignacio.settings.barDisplay.create(Component.empty())
        player.showBossBar(bar)
        timingsBar = bar
    }

    private fun removeTimingsBar() {
        val bar = timingsBar ?: return
        player.hideBossBar(bar)
        this.timingsBar = null
    }

    fun updateDebugFlags(flags: Collection<PlayerDebugFlag>) {
        if (flags.contains(PlayerDebugFlag.SHOW_TIMINGS) && !debugFlags.contains(PlayerDebugFlag.SHOW_TIMINGS)) {
            addTimingsBar()
        } else if (!flags.contains(PlayerDebugFlag.SHOW_TIMINGS) && debugFlags.contains(PlayerDebugFlag.SHOW_TIMINGS)) {
            removeTimingsBar()
        }
        debugFlags = flags.toSet()
    }

    internal fun update() {
        val world = player.world
        timingsBar?.let { timingsBar ->
            val text = ignacio.worlds[world]?.let { (physics) ->
                val (median, best5, worst5) = timingStatsOf(
                    ignacio.engineTimings.getLast((ignacio.settings.engineTimings.barBuffer * 1000).toLong())
                )
                messages.barDisplay.forPhysics(
                    worldName = world.name,
                    numBodies = physics.bodies.num,
                    numActiveBodies = physics.bodies.numActive,
                    median = formatTime(median, messages),
                    best5 = formatTime(best5, messages),
                    worst5 = formatTime(worst5, messages),
                )
            } ?: messages.barDisplay.noPhysics(
                worldName = world.name
            )
            timingsBar.name(text.component())
        }
    }
}
