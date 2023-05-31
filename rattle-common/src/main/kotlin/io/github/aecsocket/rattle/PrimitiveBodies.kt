package io.github.aecsocket.rattle

import io.github.aecsocket.alexandria.ArenaKey
import io.github.aecsocket.alexandria.Render
import io.github.aecsocket.alexandria.genArena
import io.github.aecsocket.rattle.impl.RattleServer

enum class Visibility {
    VISIBLE,
    INVISIBLE,
}

data class PrimitiveBodyDesc(
    val geom: Geometry,
    val material: PhysicsMaterial,
    val type: RigidBodyType,
    val mass: Mass = Mass.Density(1.0),
    val visibility: Visibility = Visibility.VISIBLE,
    val isCcdEnabled: Boolean = false,
    val gravityScale: Real = 1.0,
    val linearDamping: Real = DEFAULT_LINEAR_DAMPING,
    val angularDamping: Real = DEFAULT_ANGULAR_DAMPING,
)

interface PrimitiveBodyKey

interface PrimitiveBodies<W> {
    val count: Int

    fun create(location: Location<W>, desc: PrimitiveBodyDesc): PrimitiveBodyKey

    fun destroy(key: PrimitiveBodyKey)

    fun destroyAll()
}

abstract class AbstractPrimitiveBodies<W>(
    private val server: RattleServer<W, *>,
) : PrimitiveBodies<W> {
    private inner class Instance(
        val world: W,
        val shape: Shape,
        val body: RigidBody,
        val collider: Collider,
        val render: Render?,
    ) {
        var nextPosition: Iso? = null
        private val destroyed = DestroyFlag()

        fun destroy() {
            destroyed()
            // todo despawn render
            server.rattle.runTask {
                server.physicsOrNull(world)?.withLock { (physics) ->
                    physics.colliders.remove(collider)
                    physics.bodies.remove(body)
                }
                collider.destroy()
                body.destroy()
                shape.release()
            }
        }
    }

    @JvmInline
    value class Key(val key: ArenaKey) : PrimitiveBodyKey

    private val instances = genArena<Instance>()

    override val count: Int
        get() = instances.size

    fun onPhysicsStep() {
        instances.forEach { (_, instance) ->
            instance.body.read { rb ->
                if (!rb.isSleeping) {
                    instance.nextPosition = rb.position
                }
            }
        }
    }

    override fun create(location: Location<W>, desc: PrimitiveBodyDesc): PrimitiveBodyKey {
        val engine = server.rattle.engine

        val shape = engine.createShape(desc.geom)
        val body = engine.createBody(
            position = Iso(location.position),
            type = desc.type,
            isCcdEnabled = desc.isCcdEnabled,
            gravityScale = desc.gravityScale,
            linearDamping = desc.linearDamping,
            angularDamping = desc.angularDamping,
        )
        val collider = engine.createCollider(
            shape = shape,
            material = desc.material,
            mass = desc.mass,
        )
        val render: Render? = null // TODO

        val instance = Instance(
            world = location.world,
            shape = shape,
            body = body,
            collider = collider,
            render = render,
        )
        val key = instances.insert(instance)

        server.rattle.runTask {
            server.physicsOrCreate(location.world).withLock { (physics) ->
                physics.bodies.add(body)
                physics.colliders.add(collider)
                collider.write { it.parent = body }
            }
        }

        return Key(key)
    }

    override fun destroy(key: PrimitiveBodyKey) {
        key as Key
        instances.remove(key.key)?.destroy()
    }

    override fun destroyAll() {
        instances.forEach { (_, instance) ->
            instance.destroy()
        }
        instances.clear()
    }
}
