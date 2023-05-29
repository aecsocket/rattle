package io.github.aecsocket.rattle

interface WorldHook {
    fun enable()

    fun disable()
}

interface TerrainStrategy : WorldHook {

}

object NoOpTerrainStrategy : TerrainStrategy {
    override fun enable() {}
    override fun disable() {}
}

interface EntityStrategy : WorldHook {

}

object NoOpEntityStrategy : EntityStrategy {
    override fun enable() {}
    override fun disable() {}
}

/*

  # Concurrency

  WorldPhysics as an object stores a lot of state that must be safely accessed by callers.
  This is implemented not on WorldPhysics itself, but by wrapping classes and methods that
  return a WorldPhysics object (by e.g. returning a Synchronized<WorldPhysics>, requiring
  callers to lock the object before use).

  `physics` is responsible for maintaining the state of physics objects, and as such must
  be synchronized against when performing updates. In this way, multiple threads can work
  on all physics states of all worlds at once, while making sure the *space accesses* do
  not lead to data races.

  However, the objects that are used whilst processing a space
  (such as a rigid body that you want to add to a space) are **not** synchronized by default,
  therefore the caller is responsible for what threads these objects are accessed by. If the
  object was just created, then it's fine to do no syncing (since our thread fully owns all
  references to the data). Otherwise, make sure to consider what your object is doing.

  The strategy fields, `terrain` and `entities`, are mostly, as far as the public API is
  concerned, read-only. However, they do still contain methods (`enable`, `disable`) which
  mutate their internal state, therefore they must still be locked by some mechanism. We
  wouldn't want a terrain strategy to stop working in the middle of processing a physics step.
  Therefore, its locking is controlled by the WorldPhysics' container (Synchronized and the like).

 */

interface WorldPhysics<W> : Destroyable {
    val world: W
    val physics: PhysicsSpace
    val terrain: TerrainStrategy
    val entities: EntityStrategy

    operator fun component1() = physics

    operator fun component2() = world
}
