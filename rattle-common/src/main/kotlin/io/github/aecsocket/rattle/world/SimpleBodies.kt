package io.github.aecsocket.rattle.world

import io.github.aecsocket.alexandria.ArenaKey
import io.github.aecsocket.alexandria.GenArena
import io.github.aecsocket.alexandria.ItemRender
import io.github.aecsocket.alexandria.desc.ItemDesc
import io.github.aecsocket.klam.FAffine3
import io.github.aecsocket.klam.FVec3
import io.github.aecsocket.rattle.*
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
        val renderInterpolationDuration: Int = 2,
        val box: ForGeometry? = null,
        val sphere: ForGeometry? = null,
    ) {
        @ConfigSerializable
        data class ForGeometry(
            @Required val item: ItemDesc,
            val scale: FVec3 = FVec3.One,
        )
    }

    interface BakedItem

    inner class Instance(
        val collider: ColliderKey,
        val body: RigidBodyKey,
        val scale: FVec3,
        val item: BakedItem,
        position: Iso,
    ) {
        val destroyed = DestroyFlag()
        var render: ItemRender? = null
        var nextPosition: Iso = position

        internal fun destroy() {
            destroyed()
            physics.colliders.remove(collider)?.destroy()
            physics.rigidBodies.remove(body)?.destroy()
            render?.despawn()
        }

        fun onTrack(render: ItemRender) {
            render
                .transform(FAffine3(
                    rotation = nextPosition.rotation.toFloat(),
                    scale = scale,
                ))
                .interpolationDuration(settings.renderInterpolationDuration)
                .item(item)
        }

        fun onUntrack(render: ItemRender) {
            render.despawn()
        }
    }

    protected abstract fun ItemRender.item(item: BakedItem)

    protected abstract fun ItemDesc.create(): BakedItem

    private val destroyed = DestroyFlag()
    private val instances = GenArena<Instance>()
    private val colliderToInstance = HashMap<ColliderKey, ArenaKey>()

    val count: Int
        get() = instances.size

    override fun destroy() {
        destroyed()
        removeAll()
    }

    operator fun get(key: ArenaKey) = instances[key]

    fun byCollider(collKey: ColliderKey): Instance? {
        val arenaKey = colliderToInstance[collKey] ?: return null
        return instances[arenaKey] ?: run {
            colliderToInstance.remove(collKey)
            null
        }
    }

    protected abstract fun defaultGeomSettings(): Settings.ForGeometry

    protected abstract fun createRender(position: Vec, instKey: ArenaKey): ItemRender

    fun create(position: Iso, desc: SimpleBodyDesc): ArenaKey {
        val engine = platform.rattle.engine

        // SAFETY: we don't increment the ref count, so `collider` will fully own this shape
        val shape = engine.createShape(desc.geom.handle)
        val collider = engine.createCollider(shape, StartPosition.Relative())
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

        val (rawGeomSettings, rawGeomScale) = when (val geom = desc.geom) {
            is SimpleGeometry.Sphere -> settings.sphere to Vec(geom.handle.radius * 2.0)
            is SimpleGeometry.Box -> settings.box to geom.handle.halfExtent * 2.0
        }
        val geomSettings = rawGeomSettings ?: defaultGeomSettings()
        val geomScale = rawGeomScale.toFloat() * geomSettings.scale

        val inst = Instance(
            collider = collider,
            body = body,
            scale = geomScale,
            item = geomSettings.item.create(),
            position = position,
        )
        val instKey = instances.insert(inst)
        inst.render = when (desc.visibility) {
            Visibility.VISIBLE -> createRender(position.translation, instKey)
            Visibility.INVISIBLE -> null
        }
        return instKey
    }

    fun remove(key: ArenaKey) {
        instances.remove(key)?.destroy()
    }

    open fun removeAll() {
        instances.forEach { (_, instance) ->
            instance.destroy()
        }
        instances.clear()
    }

    fun onPhysicsStep() {
        instances.forEach { (_, instance) ->
            val body = physics.rigidBodies.read(instance.body) ?: return@forEach
            if (!body.isSleeping) {
                instance.nextPosition = body.position
            }
        }
    }
}
