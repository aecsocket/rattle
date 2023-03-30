package io.github.aecsocket.ignacio.paper

import net.kyori.adventure.bossbar.BossBar
import org.bukkit.entity.Player

class IgnacioPlayer internal constructor(
    private val ignacio: Ignacio,
    val player: Player,
) {
    data class DebugFlags(
        val showTimings: Boolean = false,
    )

    private fun createMessages() = ignacio.messages.forLocale(player.locale())

    private var messages = createMessages()
    var debugFlags = DebugFlags()
    var timingsBar: BossBar? = null
        private set

    internal fun updateMessages() {
        messages = createMessages()
    }

    internal fun update() {

    }
}