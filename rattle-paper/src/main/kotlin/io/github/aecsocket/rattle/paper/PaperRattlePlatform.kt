package io.github.aecsocket.rattle.paper

import io.github.aecsocket.rattle.impl.CommandSource
import io.github.aecsocket.rattle.impl.RattlePlatform
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player

class PaperRattlePlatform(
    private val plugin: PaperRattle,
) : RattlePlatform<World>(plugin.rattle) {
    override val worlds: Iterable<World>
        get() = Bukkit.getWorlds()

    override fun callBeforeStep(dt: Double) {
        RattleEvents.BeforePhysicsStep(dt).callEvent()
    }

    override fun asPlayer(sender: CommandSource) =
        ((sender as PaperCommandSource).handle as? Player)?.let { plugin.playerData(it) }

    override fun key(world: World) = world.key()

    override fun physicsOrNull(world: World) =
        plugin.physicsOrNull(world)

    override fun physicsOrCreate(world: World) =
        plugin.physicsOrCreate(world)
}
