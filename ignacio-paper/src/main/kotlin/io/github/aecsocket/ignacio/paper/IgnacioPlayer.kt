package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.glossa.core.component
import io.github.aecsocket.ignacio.core.math.Ray
import io.github.aecsocket.ignacio.core.math.d
import io.github.aecsocket.ignacio.paper.util.location
import io.github.aecsocket.ignacio.paper.util.position
import io.github.aecsocket.ignacio.paper.util.vec3f
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.Particle
import org.bukkit.entity.Player
import java.util.Locale

data class PlayerDebugFlags(
    val showTimings: Boolean = false,
    val bodyInfo: Boolean = false,
)

class IgnacioPlayer internal constructor(
    private val ignacio: Ignacio,
    val player: Player
) {
    private var messages = createMessages(player.locale())
    var debugFlags: PlayerDebugFlags = PlayerDebugFlags()
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

    fun updateDebugFlags(newFlags: PlayerDebugFlags) {
        if (newFlags.showTimings && !debugFlags.showTimings) {
            addTimingsBar()
        } else if (!newFlags.showTimings && debugFlags.showTimings) {
            removeTimingsBar()
        }
        debugFlags = newFlags
    }

    internal fun update() {
        val world = player.world
        timingsBar?.let { timingsBar ->
            val text = ignacio.worlds[world]?.let { (physics) ->
                val (median, best5, worst5) = timingStatsOf(
                    ignacio.engineTimings.getLast((ignacio.settings.engineTimings.barBuffer * 1000).toLong())
                )
                messages.debug.showTimings.forPhysics(
                    worldName = world.name,
                    numBodies = physics.bodies.num,
                    numActiveBodies = physics.bodies.numActive,
                    median = formatTime(median, messages),
                    best5 = formatTime(best5, messages),
                    worst5 = formatTime(worst5, messages),
                )
            } ?: messages.debug.showTimings.noPhysics(
                worldName = world.name
            )
            timingsBar.name(text.component())
        }

        if (debugFlags.bodyInfo) {
            ignacio.engine.launchTask {
                val (physics) = ignacio.worlds[world] ?: return@launchTask
                val ray = Ray(player.eyeLocation.position(), player.location.direction.vec3f())
                val text = physics.narrowQuery.rayCastBody(
                    ray,
                    32.0f, // todo
                )?.let { rayCast ->
                    repeat(10) {
                        val pos = rayCast.inPosition + (rayCast.normal * (0.1f * it)).d()
                        player.spawnParticle(Particle.FLAME, pos.location(world), 0)
                    }

                    messages.debug.bodyInfo.forBody(
                        bodyName = rayCast.body.name ?: "-",
                        inDistance = rayCast.inDistance,
                    )
                } ?: messages.debug.bodyInfo.noBody()
                player.sendActionBar(text.component())
            }
        }
    }
}
