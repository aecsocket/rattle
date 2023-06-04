package io.github.aecsocket.rattle.impl

import io.github.aecsocket.glossa.component
import io.github.aecsocket.rattle.RattleMessages
import io.github.aecsocket.rattle.stats.formatTiming
import io.github.aecsocket.rattle.stats.timingStatsOf
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component

private val spinnerStates = listOf("|", "|", "/", "/", "-", "-", "\\", "\\")

abstract class RattlePlayer<W, P : Audience>(
    private val platform: RattlePlatform<W, *>,
    val player: P,
) {
    private var spinnerState = 0

    abstract val messages: RattleMessages
    abstract val world: W

    private var statsBar: BossBar? = null

    protected abstract fun P.showBar(bar: BossBar)

    protected abstract fun P.hideBar(bar: BossBar)

    fun showStatsBar(enabled: Boolean) {
        val statsBar = statsBar
        if (enabled && statsBar == null) {
            this.statsBar = platform.rattle.settings.stats.timingBar
                .create(Component.empty())
                .also { player.showBar(it) }
        } else if (!enabled && statsBar != null) {
            player.hideBar(statsBar)
            this.statsBar = null
        }
    }

    fun tick() {
        val world = world
        statsBar?.let { statsBar ->
            val (median, best5, worst5) = timingStatsOf(
                platform.engineTimings.getLast(
                    (platform.rattle.settings.stats.timingBarBuffer * 1000).toLong()
                )
            )

            val text = platform.physicsOrNull(world)?.withLock { (physics) ->
                messages.statsBar.some(
                    world = platform.key(world).asString(),
                    numBodies = physics.bodies.count,
                    numActiveBodies = physics.bodies.activeCount,
                    median = formatTiming(median, messages),
                    best5 = formatTiming(best5, messages),
                    worst5 = formatTiming(worst5, messages),
                )
            } ?: messages.statsBar.none(
                world = platform.key(world).asString(),
                median = formatTiming(median, messages),
                best5 = formatTiming(best5, messages),
                worst5 = formatTiming(worst5, messages),
            )

            // TODO spinner actually configurable
            statsBar.name(text.component().append(Component.text(" ${spinnerStates[spinnerState]}")))
            spinnerState = (spinnerState + 1) % spinnerStates.size
        }
    }
}
