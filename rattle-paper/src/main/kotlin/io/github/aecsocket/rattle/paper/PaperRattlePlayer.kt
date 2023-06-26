package io.github.aecsocket.rattle.paper

import io.github.aecsocket.alexandria.paper.extension.direction
import io.github.aecsocket.alexandria.paper.extension.position
import io.github.aecsocket.klam.*
import io.github.aecsocket.rattle.impl.RattleMessages
import io.github.aecsocket.rattle.impl.RattlePlayer
import org.bukkit.entity.Player

class PaperRattlePlayer(
    override val platform: PaperRattlePlatform,
    val player: Player,
) : RattlePlayer(platform) {
    override var messages: RattleMessages = platform.plugin.messages.forLocale(player.locale())

    override val world: RWorld
        get() = player.world.wrap()

    override fun audience() = player

    override fun eyePosition(): DVec3 {
        return player.eyeLocation.position()
    }

    override fun eyeDirection(): DVec3 {
        return player.eyeLocation.direction()
    }
}
