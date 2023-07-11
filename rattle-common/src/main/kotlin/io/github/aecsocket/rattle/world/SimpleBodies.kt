package io.github.aecsocket.rattle.world

import io.github.aecsocket.alexandria.ItemRender
import io.github.aecsocket.alexandria.desc.ItemDesc
import io.github.aecsocket.alexandria.desc.ItemType
import io.github.aecsocket.kbeam.ArenaKey
import io.github.aecsocket.kbeam.Dirty
import io.github.aecsocket.kbeam.GenArena
import io.github.aecsocket.klam.*
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.impl.RattlePlatform
import net.kyori.adventure.key.Key
import org.spongepowered.configurate.objectmapping.ConfigSerializable

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
    val mass: Collider.Mass = Collider.Mass.Density(1.0),
    val visibility: Visibility = Visibility.VISIBLE,
    val isCcdEnabled: Boolean = false,
    val linearVelocity: DVec3 = DVec3.zero,
    val angularVelocity: DVec3 = DVec3.zero,
    val gravityScale: Double = 1.0,
    val linearDamping: Double = DEFAULT_LINEAR_DAMPING,
    val angularDamping: Double = DEFAULT_ANGULAR_DAMPING,
)

abstract class SimpleBodies(
    protected open val platform: RattlePlatform,
    // SAFETY: while a caller has access to a SimpleBodies object, they also have access to the containing
    // WorldPhysics, and therefore the PhysicsSpace is locked
    private val physics: PhysicsSpace,
    val settings: Settings = Settings(),
) : Destroyable {
    @ConfigSerializable
    data class Settings(
        val renderInterpolationDuration: Int = 2,
        val box: ForGeometry = ForGeometry(),
        val sphere: ForGeometry = ForGeometry(),
    ) {
        @ConfigSerializable
        data class ForGeometry(
            val item: ItemDesc = ItemDesc(ItemType.Keyed(Key.key("minecraft", "stone"))),
            val scale: FVec3 = FVec3.one,
        )
    }

    abstract inner class Instance(
        val collider: ColliderKey,
        val body: RigidBodyKey,
        private val scale: FVec3,
        position: DIso3,
    ) {
        val destroyed = DestroyFlag()
        open val render: ItemRender? = null
        private val mPosition = Dirty(position)
        var position by mPosition

        internal fun destroy() {
            destroyed()
            physics.colliders.remove(collider)?.destroy()
            physics.rigidBodies.remove(body)?.destroy()
            render?.despawn()
        }

        protected abstract fun ItemRender.item()

        fun onTrack(render: ItemRender) {
            render
                .spawn(position.translation)
                .transform(FAffine3(FVec3.zero, position.rotation.toFloat(), scale))
                .interpolationDuration(settings.renderInterpolationDuration)
                .item()
        }

        fun onUntrack(render: ItemRender) {
            render.despawn()
        }

        fun onUpdate(): Boolean {
            if (!mPosition.clean()) return false
            val render = render ?: return false
            render
                // this is required to make the interpolation work properly
                // because this game SUCKS
                .interpolationDelay(0)
                .position(position.translation)
                .transform(FAffine3(FVec3.zero, position.rotation.toFloat(), scale))
            return true
        }
    }

    private val destroyed = DestroyFlag()
    protected val instances = GenArena<Instance>()
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

    protected abstract fun addInstance(
        collider: ColliderKey,
        body: RigidBodyKey,
        scale: FVec3,
        position: DIso3,
        geomSettings: Settings.ForGeometry,
        visibility: Visibility,
    ): ArenaKey

    fun create(position: DIso3, desc: SimpleBodyDesc): ArenaKey {
        val engine = platform.rattle.engine

        // SAFETY: we don't increment the ref count, so `collider` will fully own this shape
        val shape = engine.createShape(desc.geom.handle)
        val collider = engine.createCollider(shape, Collider.Start.Relative())
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

        val (geomSettings, rawGeomScale) = when (val geom = desc.geom) {
            is SimpleGeometry.Sphere -> settings.sphere to DVec3(geom.handle.radius * 2.0)
            is SimpleGeometry.Box -> settings.box to geom.handle.halfExtent * 2.0
        }
        val geomScale = rawGeomScale.toFloat() * geomSettings.scale

        val instKey = addInstance(
            collider = collider,
            body = body,
            scale = geomScale,
            position = position,
            geomSettings = geomSettings,
            visibility = desc.visibility,
        )
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

    open fun onPhysicsStep() {
        instances.forEach { (_, instance) ->
            val body = physics.rigidBodies.read(instance.body) ?: return@forEach
            if (!body.isSleeping) {
                instance.position = body.position
            }
        }
    }
}
