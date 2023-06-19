package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.alexandria.fabric.extension.toDVec
import io.github.aecsocket.rattle.impl.RattleMessages
import io.github.aecsocket.rattle.Vec
import io.github.aecsocket.rattle.impl.RattlePlayer
import net.kyori.adventure.bossbar.BossBar
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer

@Suppress("UnstableApiUsage")
class FabricRattlePlayer(
    rattle: FabricRattle,
    player: ServerPlayer,
) : RattlePlayer<ServerLevel, ServerPlayer>(player.server.rattle(), player) {
    val rattle = player.server.rattle()
    // Sometimes, when a player is placed into a world, we get an error that `ForwardingAudience$Single.audience()` is null
    // (if we access `player.get(Identity.LOCALE)` through Adventure's pointer mechanism)
    // Therefore, the Fabric Adventure platform has not initialized our player yet (for some reason).
    // So instead, we will initialize the messages with a default locale, and hope that the PlayerLocales.CHANGED_EVENT
    // changes to the player's actual locale later
    override var messages: RattleMessages = rattle.messages.forLocale(rattle.settings.defaultLocale)

    override val world: ServerLevel
        get() = player.getLevel()

    override fun ServerPlayer.showBar(bar: BossBar) {
        rattle.bossBars.subscribe(player, bar)
    }

    override fun ServerPlayer.hideBar(bar: BossBar) {
        rattle.bossBars.unsubscribe(player, bar)
    }

    override fun eyePosition(): Vec {
        return player.eyePosition.toDVec()
    }

    override fun eyeDirection(): Vec {
        return Vec(player.xRot.toDouble(), player.yRot.toDouble(), 0.0)
    }
}
