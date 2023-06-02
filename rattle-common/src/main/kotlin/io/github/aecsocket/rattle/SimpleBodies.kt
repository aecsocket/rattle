package io.github.aecsocket.rattle

import io.github.aecsocket.alexandria.ArenaKey
import io.github.aecsocket.alexandria.genArena
import io.github.aecsocket.alexandria.sync.Locked
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
    val world: W,
    private val platform: RattlePlatform<W, *>,
    // SAFETY: while a caller has access to a SimpleBodies object, they also have access to the containing
    // WorldPhysics, and therefore the PhysicsSpace is locked
    private val physics: PhysicsSpace,
) : SimpleBodies<W>, Destroyable {
    inner class Instance(
        val shape: Shape,
        val body: RigidBodyHandle,
        val collider: ColliderHandle,
    ) {
        var nextPosition: Iso? = null
        val destroyed = DestroyFlag()

        internal fun destroy() {
            destroyed()
            platform.rattle.runTask {
                platform.physicsOrNull(world)?.withLock { (physics) ->
                    physics.colliders.remove(collider)?.destroy()
                    physics.bodies.remove(body)?.destroy()
                }
                shape.release()
            }
        }
    }

    @JvmInline
    private value class Key(val key: ArenaKey) : SimpleBodyKey

    private val destroyed = DestroyFlag()
    private val instances = Locked(genArena<Instance>())

    override val count: Int
        get() = instances.withLock { it.size }

    fun onPhysicsStep() {
        instances.withLock { instances ->
            instances.forEach { (_, instance) ->
                physics.bodies.read(instance.body)?.let { body ->
                    if (!body.isSleeping) {
                        instance.nextPosition = body.position
                    }
                }
            }
        }
    }

    override fun destroy() {
        destroyed()
        destroyAll()
    }

    protected abstract fun createVisual(
        position: Iso,
        desc: SimpleBodyDesc,
        instance: Instance,
        instanceKey: ArenaKey,
    )

    override fun create(position: Iso, desc: SimpleBodyDesc): SimpleBodyKey {
        val engine = platform.rattle.engine

        val shape = engine.createShape(desc.geom)
        val body = engine.createBody(
            type = desc.type,
            position = position,
            isCcdEnabled = desc.isCcdEnabled,
            gravityScale = desc.gravityScale,
            linearDamping = desc.linearDamping,
            angularDamping = desc.angularDamping,
        ).let { physics.bodies.add(it) }
        val collider = engine.createCollider(
            shape = shape,
            material = desc.material,
            mass = desc.mass,
        ).let { physics.colliders.add(it) }
        physics.attach(collider, body)

        val instance = Instance(
            shape = shape,
            body = body,
            collider = collider,
        )
        val key = instances.withLock { it.insert(instance) }
        when (desc.visibility) {
            Visibility.VISIBLE -> createVisual(position, desc, instance, key)
            Visibility.INVISIBLE -> {}
        }

        return Key(key)
    }

    fun destroy(key: ArenaKey) {
        instances.withLock { it.remove(key) }?.destroy()
    }

    override fun destroy(key: SimpleBodyKey) {
        key as Key
        destroy(key.key)
    }

    override fun destroyAll() {
        instances.withLock { instances ->
            instances.forEach { (_, instance) ->
                instance.destroy()
            }
            instances.clear()
        }
    }
}
