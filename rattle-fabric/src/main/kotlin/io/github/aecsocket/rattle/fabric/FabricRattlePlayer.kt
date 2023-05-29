package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.alexandria.hook.fallbackLocale
import io.github.aecsocket.rattle.RattleMessages
import io.github.aecsocket.rattle.RattlePlayer
import net.kyori.adventure.bossbar.BossBar
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer

class FabricRattlePlayer(
    player: ServerPlayer,
) : RattlePlayer<ServerLevel, ServerPlayer>(Rattle, player) {
    override var messages: RattleMessages = Rattle.messages.forLocale(fallbackLocale)

    override val world: ServerLevel
        get() = player.getLevel()

    override fun ServerPlayer.showBar(bar: BossBar) {
        Rattle.adventure?.bossBars?.subscribe(player, bar)
    }

    override fun ServerPlayer.hideBar(bar: BossBar) {
        Rattle.adventure?.bossBars?.unsubscribe(player, bar)
    }
}
