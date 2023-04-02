package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.ignacio.DestroyFlag
import io.github.aecsocket.ignacio.Destroyable
import io.github.aecsocket.ignacio.IgnacioEngine
import io.github.aecsocket.ignacio.PhysicsSpace
import org.bukkit.World

class PhysicsWorld(
    val engine: IgnacioEngine,
    val world: World,
    val physics: PhysicsSpace,
) : Destroyable {
    private val destroyed = DestroyFlag()

    override fun destroy() {
        destroyed.mark()
    }
}
