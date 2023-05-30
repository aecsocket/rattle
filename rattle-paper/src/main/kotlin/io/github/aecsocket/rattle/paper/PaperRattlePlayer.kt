package io.github.aecsocket.rattle.paper

import io.github.aecsocket.rattle.RattleMessages
import io.github.aecsocket.rattle.RattlePlayer
import net.kyori.adventure.bossbar.BossBar
import org.bukkit.World
import org.bukkit.entity.Player

class PaperRattlePlayer(
    rattle: RattlePlugin,
    player: Player,
) : RattlePlayer<World, Player>(rattle, player) {
    override var messages: RattleMessages = rattle.messages.forLocale(player.locale())

    override val world: World
        get() = player.world

    override fun Player.showBar(bar: BossBar) {
        this.showBossBar(bar)
    }

    override fun Player.hideBar(bar: BossBar) {
        this.hideBossBar(bar)
    }
}
