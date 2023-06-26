package io.github.aecsocket.rattle.paper

import io.github.aecsocket.alexandria.paper.extension.direction
import io.github.aecsocket.alexandria.paper.extension.position
import io.github.aecsocket.klam.*
import io.github.aecsocket.rattle.impl.RattleMessages
import io.github.aecsocket.rattle.impl.RattlePlayer
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.bossbar.BossBar
import org.bukkit.World
import org.bukkit.entity.Player

class PaperRattlePlayer(
    private val rattle: PaperRattle,
    val player: Player,
) : RattlePlayer<World>(rattle.platform) {
    override var messages: RattleMessages = rattle.messages.forLocale(player.locale())

    override val world: World
        get() = player.world

    override fun audience() = player

    override fun showBar(bar: BossBar) {
        player.showBossBar(bar)
    }

    override fun hideBar(bar: BossBar) {
        player.hideBossBar(bar)
    }

    override fun eyePosition(): DVec3 {
        return player.eyeLocation.position()
    }

    override fun eyeDirection(): DVec3 {
        return player.eyeLocation.direction()
    }

    override fun updateDraw(draw: Draw) {
        rattle.runTask {
            rattle.physicsOrNull(world)?.withLock { physics ->
                //(physics.terrain as? PaperDynamicTerrain)?.onUntrackChunk(player, )
            }
        }
    }
}
