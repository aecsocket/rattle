package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.rattle.CommandSource
import io.github.aecsocket.rattle.World
import io.github.aecsocket.rattle.impl.RattlePlatform
import io.github.aecsocket.rattle.impl.RattlePlayer
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

class FabricRattlePlatform(
    private val mod: FabricRattle,
    private val server: MinecraftServer,
) : RattlePlatform(mod.rattle) {
    override val worlds: Iterable<World>
        get() = server.allLevels.map { it.wrap() }

    override fun key(world: World) = world.unwrap().dimension().key()

    override fun physicsOrNull(world: World) =
        mod.physicsOrNull(world.unwrap())

    override fun physicsOrCreate(world: World) =
        mod.physicsOrCreate(world.unwrap())

    override fun asPlayer(sender: CommandSource) =
        (sender.unwrap().entity as? ServerPlayer)?.let { mod.playerData(it) }

    override fun setPlayerDraw(player: RattlePlayer, draw: RattlePlayer.Draw?) {

    }

    override fun onTick() {
        server.allLevels.forEach { level ->
            mod.physicsOrNull(level)?.withLock { physics ->
                physics.onTick()
            }
        }
        super.onTick()
    }
}
