package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.rattle.stats.plusAssign
import net.minecraft.server.MinecraftServer

internal fun stepServer(server: MinecraftServer) {
    val start = System.nanoTime()

    val worlds = server.allLevels
        .mapNotNull { world -> world.physicsOrNull() }

    worlds.forEach { world ->
        world.tick()
    }

    Rattle.engine.stepSpaces(
        0.05 * Rattle.settings.timeStepMultiplier,
        worlds.map { it.physics },
    )

    val end = System.nanoTime()
    Rattle.engineTimings += (end - start)
}
