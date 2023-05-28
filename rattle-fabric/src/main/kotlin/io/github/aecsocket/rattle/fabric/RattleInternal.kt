package io.github.aecsocket.rattle.fabric

fun onServerTick() {
    if (Rattle.stepping.getAndSet(true)) return

    Rattle.engine.stepSpaces(
        0.05 * Rattle.settings.timeStepMultiplier,
        Rattle.physics.map { (_, world) -> world.physics }
    )

    Rattle.stepping.set(false)
}
