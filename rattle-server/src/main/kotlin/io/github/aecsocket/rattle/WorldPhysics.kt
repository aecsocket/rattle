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

interface WorldPhysics<W> {
    val physics: PhysicsSpace
    val world: W
    val terrain: TerrainStrategy
    val entities: EntityStrategy

    operator fun component1() = world
}
