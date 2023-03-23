package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.alexandria.core.math.Vec3d
import io.github.aecsocket.alexandria.core.math.Vec3f
import io.github.aecsocket.ignacio.core.IgnacioEngine
import io.github.aecsocket.ignacio.core.PhysicsSpace
import io.github.aecsocket.ignacio.paper.util.position
import org.bukkit.World
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class DefaultPlayerStrategy(
    private val engine: IgnacioEngine,
    private val world: World,
    private val physics: PhysicsSpace,
) : PlayerStrategy {
    private val filter = engine.filters.createBroad(
        broad = { layer -> layer == engine.layers.ofBroadPhase.static || layer == engine.layers.ofBroadPhase.moving },
        objects = { true },
    )
    private val toProcess: MutableSet<Vec3d> = Collections.newSetFromMap(ConcurrentHashMap())

    var enabled = true
        private set

    override fun destroy() {
        filter.destroy()
    }

    override fun enable() {
        enabled = true
    }

    override fun disable() {
        enabled = false
    }

    override fun tickUpdate() {
        if (!enabled) return
        world.players.forEach { player ->
            toProcess += player.location.position()
        }
    }

    override fun physicsUpdate(deltaTime: Float) {
        toProcess.forEach { position ->
            physics.broadQuery.overlapSphere(
                position = position,
                radius = 4.0f,
                filter = filter,
            ).forEach { body ->
                body.readUnlocked { bodyRead ->
                    // TODO compound shapes
                    val bodySupport = engine.gjk.supportOf(bodyRead.shape) ?: return@readUnlocked
                    val pointSupport = engine.gjk.supportOf(Vec3f(position - bodyRead.position))
                    val (closestA, closestB) = engine.gjk.closestPoints(bodySupport, pointSupport)
                }
            }
        }
        toProcess.clear()
    }
}
