package io.github.aecsocket.rattle.impl

import io.github.aecsocket.glossa.component
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.stats.formatTiming
import io.github.aecsocket.rattle.stats.timingStatsOf
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.audience.ForwardingAudience
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component

private const val BOSS_BAR_FLASH_INTERVAL = 5L
private val bossBarColorStates = listOf(
    BossBar.Color.YELLOW,
    BossBar.Color.GREEN,
)

abstract class RattlePlayer<W, P : Audience>(
    private val platform: RattlePlatform<W, *>,
    val player: P,
) : ForwardingAudience.Single {
    data class Launcher(
        val geom: SimpleGeometry,
        val material: PhysicsMaterial,
        val velocity: Real,
        val mass: Mass,
        val isCcdEnabled: Boolean,
    )

    abstract val messages: RattleMessages
    abstract val world: W

    private var statsBar: BossBar? = null
    var launcher: Launcher? = null

    override fun audience() = player

    protected abstract fun P.showBar(bar: BossBar)

    protected abstract fun P.hideBar(bar: BossBar)

    protected abstract fun eyePosition(): Vec

    protected abstract fun eyeDirection(): Vec

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

            // SAFETY: we will only access atomic fields, not any stateful fields, so we're fine
            val (text, currentTick) = platform.physicsOrNull(world)?.leak()?.let { physics ->
                messages.statsBar.some(
                    world = platform.key(world).asString(),
                    rigidBodies = physics.stats.rigidBodies,
                    activeRigidBodies = physics.stats.activeRigidBodies,
                    median = formatTiming(median, messages),
                    best5 = formatTiming(best5, messages),
                    worst5 = formatTiming(worst5, messages),
                ) to physics.currentTick
            } ?: (messages.statsBar.none(
                world = platform.key(world).asString(),
                median = formatTiming(median, messages),
                best5 = formatTiming(best5, messages),
                worst5 = formatTiming(worst5, messages),
            ) to 0L)

            statsBar.name(text.component())
            statsBar.color(bossBarColorStates[((currentTick / BOSS_BAR_FLASH_INTERVAL) % bossBarColorStates.size).toInt()])
        }
    }

    fun onClick() {
        val launcher = launcher ?: return
        platform.rattle.runTask {
            platform.physicsOrCreate(world).withLock { physics ->
                physics.simpleBodies.create(Iso(eyePosition()), SimpleBodyDesc(
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
