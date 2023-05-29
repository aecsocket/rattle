package io.github.aecsocket.rattle

import net.kyori.adventure.key.Key

data class Location<W>(
    val world: W,
    val position: Vec,
)

interface RattleAdapter<W> {
    fun key(world: W): Key

    fun physicsOrNull(world: W): WorldPhysics<W>?

    fun physicsOrCreate(world: W): WorldPhysics<W>

    fun destroyPhysics(world: W)

    fun hasPhysics(world: W) = physicsOrNull(world) != null
}
