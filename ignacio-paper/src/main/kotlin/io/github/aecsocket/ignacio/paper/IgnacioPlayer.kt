package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.glossa.core.component
import io.github.aecsocket.ignacio.core.math.clamp01
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.World
import org.bukkit.entity.Player
import java.util.Locale

class IgnacioPlayer internal constructor(
    private val ignacio: Ignacio,
    val player: Player
) {
    private var messages = createMessages(player.locale())
    var timingsBar: BossBar? = null
        private set
    private val spaceCreateBars = HashMap<World, BossBar>()

    private fun createMessages(locale: Locale) = ignacio.messages.forLocale(locale)

    internal fun updateMessages(locale: Locale) {
        messages = createMessages(locale)
    }

    fun addTimingsBar() {
        removeTimingsBar()
        val bar = ignacio.settings.barDisplay.create(Component.empty())
        player.showBossBar(bar)
        timingsBar = bar
    }

    fun removeTimingsBar() {
        val bar = timingsBar ?: return
        player.hideBossBar(bar)
        this.timingsBar = null
    }

    fun addSpaceCreateBar(world: World) {
        removeSpaceCreateBar(world)
        val bar = ignacio.settings.barDisplay.create(Component.empty())
        player.showBossBar(bar)
        spaceCreateBars[world] = bar
    }

    fun removeSpaceCreateBar(world: World) {
        val bar = spaceCreateBars[world] ?: return
        player.hideBossBar(bar)
        spaceCreateBars.remove(world)
    }

    internal fun removeWorld(world: World) {
        removeSpaceCreateBar(world)
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
                    numBodies = physics.numBodies,
                    numActiveBodies = physics.numActiveBodies,
                    median = formatTime(median, messages),
                    best5 = formatTime(best5, messages),
                    worst5 = formatTime(worst5, messages),
                )
            } ?: messages.barDisplay.noPhysics(
                worldName = world.name
            )
            timingsBar.name(text.component())
        }

        spaceCreateBars.forEach { (world, bar) ->
            val physics = ignacio.worlds[world] ?: return@forEach
            val total = world.loadedChunks.size
            val processed = physics.terrain.size
            val progress = clamp01(processed.toFloat() / total)
            bar.name(messages.barDisplay.forSpaceCreate(
                worldName = world.name,
                chunksProcessed = processed,
                chunksRemaining = total - processed,
                chunksTotal = total,
                chunksProgress = progress,
            ).component())
            bar.progress(progress)
        }
    }
}
