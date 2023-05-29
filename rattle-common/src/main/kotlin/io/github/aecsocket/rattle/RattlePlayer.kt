package io.github.aecsocket.rattle

import io.github.aecsocket.glossa.component
import io.github.aecsocket.rattle.stats.formatTiming
import io.github.aecsocket.rattle.stats.timingStatsOf
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component

abstract class RattlePlayer<W, P : Audience>(
    private val server: RattleServer<W, *>,
    val player: P,
) {
    abstract val messages: RattleMessages
    abstract val world: W

    private var statsBar: BossBar? = null

    protected abstract fun P.showBar(bar: BossBar)

    protected abstract fun P.hideBar(bar: BossBar)

    fun showStatsBar(enabled: Boolean) {
        val statsBar = statsBar
        if (enabled && statsBar == null) {
            this.statsBar = server.rattle.settings.stats.timingBar
                .create(Component.empty())
                .also { player.showBar(it) }
        } else if (!enabled && statsBar != null) {
            player.hideBar(statsBar)
            this.statsBar = null
        }
    }

    fun onTick() {
        val world = world
        statsBar?.let { statsBar ->
            val (median, best5, worst5) = timingStatsOf(
                server.engineTimings.getLast(
                    (server.rattle.settings.stats.timingBarBuffer * 1000).toLong()
                )
            )

            val text = server.physicsOrNull(world)?.withLock { (physics) ->
                messages.statsBar.some(
                    world = server.key(world).asString(),
                    numBodies = physics.bodies.count,
                    numActiveBodies = physics.bodies.activeCount,
                    median = formatTiming(median, messages),
                    best5 = formatTiming(best5, messages),
                    worst5 = formatTiming(worst5, messages),
                )
            } ?: messages.statsBar.none(
                world = server.key(world).asString(),
                median = formatTiming(median, messages),
                best5 = formatTiming(best5, messages),
                worst5 = formatTiming(worst5, messages),
            )

            statsBar.name(text.component())
        }
    }
}
