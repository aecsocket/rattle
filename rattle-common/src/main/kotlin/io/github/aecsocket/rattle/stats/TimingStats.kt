package io.github.aecsocket.rattle.stats

import io.github.aecsocket.glossa.oneLine
import io.github.aecsocket.rattle.impl.RattleMessages
import kotlin.math.max
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

data class TimingStats(
    val median: Double,
    val best5: Double,
    val worst5: Double,
)

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

fun formatTiming(time: Double, messages: RattleMessages): Component {
  val text = messages.timing(time).oneLine()
  val clampedTime = max(0.0, time)
  val color =
      when {
        clampedTime <= 15.0 -> NamedTextColor.GREEN
        clampedTime <= 50.0 -> NamedTextColor.YELLOW
        else -> NamedTextColor.RED
      }
  return text.applyFallbackStyle(color)
}
