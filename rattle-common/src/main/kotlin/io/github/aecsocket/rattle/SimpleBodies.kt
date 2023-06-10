package io.github.aecsocket.rattle

import io.github.aecsocket.alexandria.ArenaKey
import io.github.aecsocket.alexandria.GenArena
import io.github.aecsocket.alexandria.ItemRenderDesc
import io.github.aecsocket.alexandria.desc.ItemDesc
import io.github.aecsocket.alexandria.sync.Locked
import io.github.aecsocket.klam.FVec3
import io.github.aecsocket.rattle.impl.RattlePlatform
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Required

enum class Visibility {
    VISIBLE,
    INVISIBLE,
}

/**
 * A subset of [Geometry] that a [SimpleBodies] can support. Since the simple-bodies engine also handles showing
 * bodies to players, we can't display *all* shapes. This is the subset that we can support.
 */
sealed interface SimpleGeometry {
    val handle: Geometry

    data class Sphere(override val handle: io.github.aecsocket.rattle.Sphere) : SimpleGeometry

    data class Box(override val handle: io.github.aecsocket.rattle.Box) : SimpleGeometry
}

data class SimpleBodyDesc(
    val type: RigidBodyType,
    val geom: SimpleGeometry,
    val material: PhysicsMaterial,
    val mass: Mass = Mass.Density(1.0),
    val visibility: Visibility = Visibility.VISIBLE,
    val isCcdEnabled: Boolean = false,
    val linearVelocity: Vec = Vec.Zero,
    val angularVelocity: Vec = Vec.Zero,
    val gravityScale: Real = 1.0,
    val linearDamping: Real = DEFAULT_LINEAR_DAMPING,
    val angularDamping: Real = DEFAULT_ANGULAR_DAMPING,
)

interface SimpleBodyKey

abstract class SimpleBodies<W>(
    val world: W,
    private val platform: RattlePlatform<W, *>,
    // SAFETY: while a caller has access to a SimpleBodies object, they also have access to the containing
    // WorldPhysics, and therefore the PhysicsSpace is locked
    private val physics: PhysicsSpace,
    val settings: Settings = Settings(),
) : Destroyable {
    @ConfigSerializable
    data class Settings(
        val itemRenderDesc: ItemRenderDesc = ItemRenderDesc(
            interpolationDuration = 2,
        ),
        val box: Geometry? = null,
        val sphere: Geometry? = null,
    ) {
        @ConfigSerializable
        data class Geometry(
            @Required val item: ItemDesc,
            val scale: FVec3 = FVec3.One,
        )
    }

    inner class Instance(
        val collider: ColliderKey,
        val body: RigidBodyKey,
    ) {
        var nextPosition: Iso? = null
        val destroyed = DestroyFlag()

        internal fun destroy() {
            destroyed()
            platform.physicsOrNull(world)?.withLock { (physics) ->
                physics.colliders.remove(collider)?.destroy()
                physics.rigidBodies.remove(body)?.destroy()
            }
        }
    }

    @JvmInline
    private value class Key(val key: ArenaKey) : SimpleBodyKey

    private val destroyed = DestroyFlag()
    private val instances = Locked(GenArena<Instance>())

    val count: Int
        get() = instances.withLock { it.size }

    fun onPhysicsStep() {
        instances.withLock { instances ->
            instances.forEach { (_, instance) ->
                physics.rigidBodies.read(instance.body)?.let { body ->
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
        geomSettings: Settings.Geometry?,
        geomScale: Vec,
        instance: Instance,
        instanceKey: ArenaKey,
    )

    fun create(position: Iso, desc: SimpleBodyDesc): SimpleBodyKey {
        val engine = platform.rattle.engine

        // SAFETY: we don't increment the ref count, so `collider` will fully own this shape
        val shape = engine.createShape(desc.geom.handle)
        val collider = engine.createCollider(shape)
            .material(desc.material)
            .mass(desc.mass)
            .let { physics.colliders.add(it) }
        val body = engine.createBody(desc.type, position)
            .linearVelocity(desc.linearVelocity)
            .angularVelocity(desc.angularVelocity)
            .isCcdEnabled(desc.isCcdEnabled)
            .gravityScale(desc.gravityScale)
            .linearDamping(desc.linearDamping)
            .angularDamping(desc.angularDamping)
            .let { physics.rigidBodies.add(it) }
        physics.colliders.attach(collider, body)

        val instance = Instance(
            collider = collider,
            body = body,
        )
        val key = instances.withLock { it.insert(instance) }

        val (geomSettings, geomScale) = when (val geom = desc.geom) {
            is SimpleGeometry.Sphere -> settings.sphere to Vec(geom.handle.radius * 2.0)
            is SimpleGeometry.Box -> settings.box to geom.handle.halfExtent * 2.0
        }
        when (desc.visibility) {
            Visibility.VISIBLE -> createVisual(position, geomSettings, geomScale, instance, key)
            Visibility.INVISIBLE -> {}
        }

        return Key(key)
    }

    fun destroy(key: ArenaKey) {
        instances.withLock { it.remove(key) }?.destroy()
    }

    fun destroy(key: SimpleBodyKey) {
        key as Key
        platform.rattle.runTask {
            destroy(key.key)
        }
    }

    open fun destroyAll() {
        instances.withLock { instances ->
            instances.forEach { (_, instance) ->
                instance.destroy()
            }
            instances.clear()
        }
    }
}
