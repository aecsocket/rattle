package io.github.aecsocket.ignacio.rapier

import io.github.aecsocket.ignacio.*
import rapier.Rapier
import rapier.shape.SharedShape

class RapierEngine : IgnacioEngine {
    private val destroyed = DestroyFlag()

    init {
        Rapier.load()
    }

    override fun destroy() {
        destroyed()
    }

    override fun createShape(geom: Geometry): Shape {
        val handle = pushArena { arena ->
            when (geom) {
                is Sphere -> SharedShape.of(
                    rapier.shape.Ball.of(arena, geom.radius)
                )
                is Cuboid -> SharedShape.of(
                    rapier.shape.Cuboid.of(arena, geom.halfExtent.asVector(arena))
                )
                is Capsule -> SharedShape.of(
                    rapier.shape.Capsule.of(
                        arena,
                        rapier.shape.Segment.of(
                            arena,
                            Vec(0.0, -geom.halfHeight, 0.0).asVector(arena),
                            Vec(0.0,  geom.halfHeight, 0.0).asVector(arena),
                        ),
                        geom.radius,
                    )
                )
            }
        }
        return RapierShape(handle)
    }

    override fun createSpace(settings: PhysicsSpace.Settings): PhysicsSpace {
        return RapierSpace(settings)
    }
}
