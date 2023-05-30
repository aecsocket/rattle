package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.alexandria.fabric.extension.createEvent
import io.github.aecsocket.rattle.Real

fun interface BeforeStep {
    fun beforeStep(server: RattleMod.Server, dt: Real)
}

object RattleEvents {
    @JvmStatic
    val BEFORE_STEP = createEvent<BeforeStep> { callbacks ->
        BeforeStep { server, dt ->
            callbacks.forEach { it.beforeStep(server, dt) }
        }
    }
}
