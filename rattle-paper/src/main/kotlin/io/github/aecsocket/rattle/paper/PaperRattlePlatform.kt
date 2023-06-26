package io.github.aecsocket.rattle.paper

import io.github.aecsocket.rattle.CommandSource
import io.github.aecsocket.rattle.impl.RattlePlatform
import org.bukkit.Bukkit
import org.bukkit.entity.Player

class PaperRattlePlatform(
    private val plugin: PaperRattle,
) : RattlePlatform(plugin.rattle) {
    override val worlds: Iterable<RWorld>
        get() = Bukkit.getWorlds().map { it.wrap() }

    override fun callBeforeStep(dt: Double) {
        RattleEvents.BeforePhysicsStep(dt).callEvent()
    }

    override fun asPlayer(sender: CommandSource) =
        (sender.unwrap() as? Player)?.let { plugin.playerData(it) }

    override fun key(world: RWorld) = world.unwrap().key()

    override fun physicsOrNull(world: RWorld) =
        plugin.physicsOrNull(world.unwrap())

    override fun physicsOrCreate(world: RWorld) =
        plugin.physicsOrCreate(world.unwrap())
}
