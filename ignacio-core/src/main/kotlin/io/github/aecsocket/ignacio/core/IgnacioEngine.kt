package io.github.aecsocket.ignacio.core

interface IgnacioEngine : Destroyable {
    val build: String

    fun createSpace(settings: PhysicsSpace.Settings): PhysicsSpace

    fun destroySpace(space: PhysicsSpace)
}
