package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.glossa.component
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import kotlin.math.max

data class TimingStats(val median: Double, val best5: Double, val worst5: Double)

fun timingStatsOf(times: List<Long>): TimingStats {
    val sortedTimes = times.sorted()
    val median: Double
    val best5: Double
    val worst5: Double
    if (sortedTimes.isEmpty()) {
        median = 0.0
        best5 = 0.0
        worst5 = 0.0
    } else {
        fun Long.ms() = this / 1.0e6
        median = sortedTimes[(sortedTimes.size * 0.5).toInt()].ms()
        best5 = sortedTimes[(sortedTimes.size * 0.05).toInt()].ms()
        worst5 = sortedTimes[(sortedTimes.size * 0.95).toInt()].ms()
    }

    return TimingStats(median, best5, worst5)
}

private val timingColors = mapOf(
    50.0 to NamedTextColor.RED,
    15.0 to NamedTextColor.YELLOW,
    0.0 to NamedTextColor.GREEN,
)

internal fun formatTiming(time: Double, messages: IgnacioMessages): Component {
    val text = messages.timing(time).component()
    val clampedTime = max(0.0, time)
    val color = timingColors.firstNotNullOf { (threshold, color) ->
        if (clampedTime >= threshold) color else null
    }
    return text.applyFallbackStyle(color)
}
