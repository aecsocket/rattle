package io.github.aecsocket.rattle.impl

import io.github.aecsocket.glossa.component
import io.github.aecsocket.klam.*
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.stats.formatTiming
import io.github.aecsocket.rattle.stats.timingStatsOf
import io.github.aecsocket.rattle.world.SimpleBodyDesc
import io.github.aecsocket.rattle.world.SimpleGeometry
import io.github.aecsocket.rattle.world.Visibility
import net.kyori.adventure.audience.ForwardingAudience
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component

private const val STATS_BAR_STEP_TIME = 2500L

abstract class RattlePlayer(
    private val platform: RattlePlatform,
) : ForwardingAudience.Single {
    data class Draw(
        val terrain: Boolean = false,
    )

    data class Launcher(
        val geom: SimpleGeometry,
        val material: PhysicsMaterial,
        val velocity: Double,
        val mass: Mass,
        val isCcdEnabled: Boolean,
    )

    abstract val messages: RattleMessages
    abstract val world: World

    private var statsBar: BossBar? = null
    private var lastStep: Long = 0
    var launcher: Launcher? = null
    var draw: Draw = Draw()
        set(value) {
            field = value
            updateDraw(draw)
        }

    protected abstract fun showBar(bar: BossBar)

    protected abstract fun hideBar(bar: BossBar)

    protected abstract fun eyePosition(): DVec3

    protected abstract fun eyeDirection(): DVec3

    protected abstract fun updateDraw(draw: Draw)

    fun showStatsBar(enabled: Boolean) {
        val statsBar = statsBar
        if (enabled && statsBar == null) {
            this.statsBar = BossBar.bossBar(Component.empty(), 1.0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS)
                .also { showBar(it) }
        } else if (!enabled && statsBar != null) {
            hideBar(statsBar)
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

            // SAFETY: we will only access atomic fields, so we're fine
            val (text, lastStep) = platform.physicsOrNull(world)?.leak()?.let { physics ->
                messages.statsBar.some(
                    world = platform.key(world).asString(),
                    rigidBodies = physics.stats.rigidBodies,
                    activeRigidBodies = physics.stats.activeRigidBodies,
                    median = formatTiming(median, messages),
                    best5 = formatTiming(best5, messages),
                    worst5 = formatTiming(worst5, messages),
                ) to physics.lastStep
            } ?: (messages.statsBar.none(
                world = platform.key(world).asString(),
                median = formatTiming(median, messages),
                best5 = formatTiming(best5, messages),
                worst5 = formatTiming(worst5, messages),
            ) to 0L)

            val timeSinceUpdate = System.currentTimeMillis() - lastStep
            val invBarProgress = timeSinceUpdate.toFloat() / STATS_BAR_STEP_TIME

            statsBar.name(text.component())
            statsBar.progress(clamp(1.0f - (invBarProgress % 1.0f), 0.0f, 1.0f))
            statsBar.color(when {
                invBarProgress <= 1.0f -> BossBar.Color.WHITE
                invBarProgress <= 2.0f -> BossBar.Color.YELLOW
                else -> BossBar.Color.RED
            })
        }
    }

    fun onClick() {
        val launcher = launcher ?: return
        platform.rattle.runTask {
            platform.physicsOrCreate(world).withLock { physics ->
                physics.simpleBodies.create(DIso3(eyePosition()), SimpleBodyDesc(
                    type = RigidBodyType.DYNAMIC,
                    geom = launcher.geom,
                    material = launcher.material,
                    mass = launcher.mass,
                    visibility = Visibility.VISIBLE,
                    isCcdEnabled = launcher.isCcdEnabled,
                    linearVelocity = eyeDirection() * launcher.velocity,
                ))
            }
        }
    }
}
