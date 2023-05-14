package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.PhysicsEngine
import io.github.aecsocket.ignacio.PhysicsMaterial
import io.github.aecsocket.ignacio.PhysicsMaterialDesc
import jolt.Jolt
import org.spongepowered.configurate.objectmapping.ConfigSerializable

class JoltEngine(val settings: Settings) : PhysicsEngine {
    @ConfigSerializable
    data class Settings(
        val a: Boolean = false,
    )

    override lateinit var version: String
        private set

    init {
        Jolt.load()
        version = Jolt.JOLT_VERSION
    }

    override fun newMaterial(desc: PhysicsMaterialDesc): PhysicsMaterial {

    }
}
