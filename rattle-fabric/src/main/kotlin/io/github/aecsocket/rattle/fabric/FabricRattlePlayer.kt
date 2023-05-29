package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.alexandria.hook.fallbackLocale
import io.github.aecsocket.rattle.RattleMessages
import io.github.aecsocket.rattle.RattlePlayer
import net.kyori.adventure.bossbar.BossBar
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer

@Suppress("UnstableApiUsage")
class FabricRattlePlayer(player: ServerPlayer) : RattlePlayer<ServerLevel, ServerPlayer>(player.server.rattle(), player) {
    val rattle = player.server.rattle()
    override var messages: RattleMessages = Rattle.messages.forLocale(fallbackLocale)

    override val world: ServerLevel
        get() = player.getLevel()

    override fun ServerPlayer.showBar(bar: BossBar) {
        rattle.bossBars.subscribe(player, bar)
    }

    override fun ServerPlayer.hideBar(bar: BossBar) {
        rattle.bossBars.unsubscribe(player, bar)
    }
}
