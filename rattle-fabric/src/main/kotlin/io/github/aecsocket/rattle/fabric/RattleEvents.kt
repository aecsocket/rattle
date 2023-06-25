package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.alexandria.fabric.extension.createEvent

fun interface BeforeStep {
    fun beforeStep(server: FabricRattlePlatform, dt: Double)
}

object RattleEvents {
    @JvmStatic
    val BEFORE_STEP = createEvent<BeforeStep> { callbacks ->
        BeforeStep { server, dt ->
            callbacks.forEach { it.beforeStep(server, dt) }
        }
    }
}
