package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.rattle.stats.plusAssign
import net.minecraft.server.MinecraftServer

internal fun stepServer(server: MinecraftServer) {
    val start = System.nanoTime()

    // SAFETY: we bulk acquire all world locks at the start,
    // bulk process all the physics spaces (because of how the
    // Rattle API works, we have to bulk process), then
    // bulk release all locks. No other thread should be able
    // to access the worlds during this.
    val locks = server.allLevels
        .mapNotNull { world -> world.physicsOrNull() }
    val worlds = locks.map { it.lock() }

    worlds.forEach { world ->
        world.onTick()
    }

    Rattle.engine.stepSpaces(
        0.05 * Rattle.settings.timeStepMultiplier,
        worlds.map { it.physics },
    )

    locks.forEach { it.unlock() }

    val end = System.nanoTime()
    Rattle.engineTimings += (end - start)
}
