package io.github.aecsocket.rattle

import io.github.aecsocket.alexandria.sync.Sync
import io.github.aecsocket.klam.DVec3
import net.kyori.adventure.key.Key

data class Location<W>(
    val world: W,
    val position: DVec3,
)

interface RattleAdapter<W> {
    fun key(world: W): Key

    fun physicsOrNull(world: W): Sync<out WorldPhysics<W>>?

    fun physicsOrCreate(world: W): Sync<out WorldPhysics<W>>

    fun hasPhysics(world: W) = physicsOrNull(world) != null
}
