package io.gitlab.aecsocket.ignacio.bullet

import io.github.aecsocket.ignacio.core.IgnacioEngine
import io.github.aecsocket.ignacio.core.PhysicsSpace
import org.spongepowered.configurate.objectmapping.ConfigSerializable

class BulletEngine(var settings: Settings) : IgnacioEngine {
    @ConfigSerializable
    data class Settings(
        val a: Int = 0
    )

    override val build: String

    init {
        build = "???"
    }

    override fun destroy() {
        TODO("Not yet implemented")
    }

    override fun createSpace(settings: PhysicsSpace.Settings): PhysicsSpace {
        TODO("Not yet implemented")
    }

    override fun destroySpace(space: PhysicsSpace) {
        TODO("Not yet implemented")
    }
}
