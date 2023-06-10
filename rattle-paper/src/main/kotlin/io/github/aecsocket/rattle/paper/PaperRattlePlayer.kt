package io.github.aecsocket.rattle.paper

import io.github.aecsocket.alexandria.paper.extension.direction
import io.github.aecsocket.alexandria.paper.extension.position
import io.github.aecsocket.rattle.RattleMessages
import io.github.aecsocket.rattle.Vec
import io.github.aecsocket.rattle.impl.RattlePlayer
import net.kyori.adventure.bossbar.BossBar
import org.bukkit.World
import org.bukkit.entity.Player

class PaperRattlePlayer(
    rattle: PaperRattle,
    player: Player,
) : RattlePlayer<World, Player>(rattle.platform, player) {
    override var messages: RattleMessages = rattle.messages.forLocale(player.locale())

    override val world: World
        get() = player.world

    override fun Player.showBar(bar: BossBar) {
        this.showBossBar(bar)
    }

    override fun Player.hideBar(bar: BossBar) {
        this.hideBossBar(bar)
    }

    override fun eyePosition(): Vec {
        return player.eyeLocation.position()
    }

    override fun eyeDirection(): Vec {
        return player.eyeLocation.direction()
    }
}
