package io.github.aecsocket.rattle

import io.github.aecsocket.alexandria.ArenaKey
import io.github.aecsocket.alexandria.Render
import io.github.aecsocket.alexandria.genArena
import io.github.aecsocket.rattle.impl.RattlePlatform

enum class Visibility {
    VISIBLE,
    INVISIBLE,
}

data class SimpleBodyDesc(
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

interface SimpleBodyKey

interface SimpleBodies<W> {
    val count: Int

    fun create(position: Iso, desc: SimpleBodyDesc): SimpleBodyKey

    fun destroy(key: SimpleBodyKey)

    fun destroyAll()
}

abstract class AbstractSimpleBodies<W>(
    private val world: W,
    private val platform: RattlePlatform<W, *>,
) : SimpleBodies<W>, Destroyable {
    private inner class Instance(
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
            platform.rattle.runTask {
                platform.physicsOrNull(world)?.withLock { (physics) ->
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
    value class Key(val key: ArenaKey) : SimpleBodyKey

    private val destroyed = DestroyFlag()
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

    override fun destroy() {
        destroyed()
        destroyAll()
    }

    override fun create(position: Iso, desc: SimpleBodyDesc): SimpleBodyKey {
        val engine = platform.rattle.engine

        val shape = engine.createShape(desc.geom)
        val body = engine.createBody(
            position = position,
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
            shape = shape,
            body = body,
            collider = collider,
            render = render,
        )
        val key = instances.insert(instance)

        platform.rattle.runTask {
            platform.physicsOrCreate(world).withLock { (physics) ->
                physics.bodies.add(body)
                physics.colliders.add(collider)
                collider.write { it.parent = body }
            }
        }

        return Key(key)
    }

    override fun destroy(key: SimpleBodyKey) {
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
