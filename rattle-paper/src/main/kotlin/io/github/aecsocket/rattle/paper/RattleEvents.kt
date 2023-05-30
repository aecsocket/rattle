package io.github.aecsocket.rattle.paper

import io.github.aecsocket.rattle.Real
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

object RattleEvents {
    data class BeforePhysicsStep(
        val dt: Real,
    ) : Event(true) {
        companion object {
            val Handlers = HandlerList()

            @JvmStatic
            fun getHandlerList() = Handlers
        }

        override fun getHandlers() = Handlers
    }
}
