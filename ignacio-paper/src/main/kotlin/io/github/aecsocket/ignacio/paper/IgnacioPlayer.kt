package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.glossa.core.component
import net.kyori.adventure.bossbar.BossBar
import org.bukkit.entity.Player
import java.util.Locale

class IgnacioPlayer internal constructor(
    private val ignacio: Ignacio,
    val player: Player
) {
    private var messages = createMessages(player.locale())
    var timingsBar: BossBar? = null
        private set

    private fun createMessages(locale: Locale) = ignacio.messages.forLocale(locale)

    internal fun updateMessages(locale: Locale) {
        messages = createMessages(locale)
    }

    fun setTimingsBar(bar: BossBar) {
        clearTimingsBar()
        player.showBossBar(bar)
        timingsBar = bar
    }

    fun clearTimingsBar() {
        val timingsBar = timingsBar ?: return
        player.hideBossBar(timingsBar)
        this.timingsBar = null
    }

    internal fun update() {
        val world = player.world
        timingsBar?.let { timingsBar ->
            val text = ignacio.physicsInOr(world)?.let { (physics) ->
                val (median, best5, worst5) = timingStatsOf(
                    ignacio.engineTimings.getLast((ignacio.settings.engineTimings.barBuffer * 1000).toLong())
                )
                messages.timingsDisplay.forPhysics(
                    worldName = world.name,
                    numBodies = physics.numBodies,
                    numActiveBodies = physics.numActiveBodies,
                    median = formatTime(median, messages),
                    best5 = formatTime(best5, messages),
                    worst5 = formatTime(worst5, messages),
                )
            } ?: messages.timingsDisplay.noPhysics(
                worldName = world.name
            )
            timingsBar.name(text.component())
        }
    }
}
