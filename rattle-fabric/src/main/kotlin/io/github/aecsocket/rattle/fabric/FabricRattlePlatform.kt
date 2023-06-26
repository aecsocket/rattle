package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.rattle.CommandSource
import io.github.aecsocket.rattle.World
import io.github.aecsocket.rattle.impl.RattlePlatform
import net.kyori.adventure.platform.fabric.FabricServerAudiences
import net.kyori.adventure.platform.fabric.impl.server.ServerBossBarListener
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

class FabricRattlePlatform(
    private val mod: FabricRattle,
    private val server: MinecraftServer,
) : RattlePlatform(mod.rattle) {
    override val worlds: Iterable<World>
        get() = server.allLevels.map { it.wrap() }

    val audiences = FabricServerAudiences.of(server)
    @Suppress("UnstableApiUsage")
    val bossBars = ServerBossBarListener(audiences)

    override fun callBeforeStep(dt: Double) {
        RattleEvents.BEFORE_STEP.invoker().beforeStep(this, dt)
    }

    override fun asPlayer(sender: CommandSource) =
        (sender.unwrap().entity as? ServerPlayer)?.let { mod.playerData(it) }

    override fun key(world: World) = world.unwrap().dimension().key()

    override fun physicsOrNull(world: World) =
        mod.physicsOrNull(world.unwrap())

    override fun physicsOrCreate(world: World) =
        mod.physicsOrCreate(world.unwrap())

    fun onTick() {
        tick()
        server.allLevels.forEach { level ->
            mod.physicsOrNull(level)?.withLock { physics ->
                physics.onTick()
            }
        }
    }
}
