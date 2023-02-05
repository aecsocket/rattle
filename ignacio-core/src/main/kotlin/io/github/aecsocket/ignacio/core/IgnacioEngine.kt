package io.github.aecsocket.ignacio.core

interface IgnacioEngine : Destroyable {
    val version: String

    fun createSpace(settings: PhysicsSpace.Settings): PhysicsSpace

    fun destroySpace(space: PhysicsSpace)
}
