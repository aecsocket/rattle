package io.github.aecsocket.rattle.world

import io.github.aecsocket.rattle.*
import java.util.concurrent.atomic.AtomicLong

interface WorldHook {
    fun enable()

    fun disable()

    fun onPhysicsStep() {}
}

interface TerrainStrategy : WorldHook, Destroyable {

}

object NoOpTerrainStrategy : TerrainStrategy {
    override fun destroy() {}
    override fun enable() {}
    override fun disable() {}
}

interface EntityStrategy : WorldHook, Destroyable {

}

object NoOpEntityStrategy : EntityStrategy {
    override fun destroy() {}
    override fun enable() {}
    override fun disable() {}
}

/**
 * # Concurrency

 * WorldPhysics as an object stores a lot of state that must be safely accessed by callers.
 * This is implemented not on WorldPhysics itself, but by wrapping classes and methods that
 * return a WorldPhysics object (by e.g. returning a [Synchronized] of WorldPhysics, requiring
 * callers to lock the object before use).

 * [physics] is responsible for maintaining the state of physics objects, and as such must
 * be synchronized against when performing updates. In this way, multiple threads can work
 * on all physics states of all worlds at once, while making sure the *space accesses* do
 * not lead to data races.

 * However, the objects that are used whilst processing a space
 * (such as a rigid body that you want to add to a space) are **not** synchronized by default,
 * therefore the caller is responsible for what threads these objects are accessed by. If the
 * object was just created, then it's fine to do no syncing (since our thread fully owns all
 * references to the data). Otherwise, make sure to consider what your object is doing.

 * The strategy fields, [terrain] and [entities], are mostly, as far as the public API is
 * concerned, read-only. However, they do still contain methods (`enable`, `disable`) which
 * mutate their internal state, therefore they must still be locked by some mechanism. We
 * wouldn't want a terrain strategy to stop working in the middle of processing a physics step.
 * Therefore, its locking is controlled by the WorldPhysics' container (Sync and the like).
 */
abstract class WorldPhysics<W>(
    open val world: W,
    val physics: PhysicsSpace,
    val terrain: TerrainStrategy,
    val entities: EntityStrategy,
    open val simpleBodies: SimpleBodies<W>,
) : Destroyable {
    private val destroyed = DestroyFlag()

    data class Stats(
        val colliders: Int = 0,
        val rigidBodies: Int = 0,
        val activeRigidBodies: Int = 0,
    )

    private val mLastStep = AtomicLong(0)
    val lastStep: Long
        get() = mLastStep.get()

    var stats: Stats = Stats()
        internal set

    operator fun component1() = physics

    operator fun component2() = world

    protected abstract fun destroyInternal()

    override fun destroy() {
        destroyed()
        destroyInternal()
        terrain.destroy()
        entities.destroy()
        simpleBodies.destroy()
        physics.destroy()
    }

    fun onPhysicsStep() {
        mLastStep.set(System.currentTimeMillis())
        terrain.onPhysicsStep()
        entities.onPhysicsStep()
        simpleBodies.onPhysicsStep()
        stats = Stats(
            colliders = physics.colliders.count,
            rigidBodies = physics.rigidBodies.count,
            activeRigidBodies = physics.rigidBodies.activeCount,
        )
    }
}
