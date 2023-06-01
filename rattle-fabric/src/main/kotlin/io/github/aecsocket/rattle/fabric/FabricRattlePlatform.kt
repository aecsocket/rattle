package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.alexandria.fabric.DisplayRenders
import io.github.aecsocket.rattle.Real
import io.github.aecsocket.rattle.impl.RattlePlatform
import net.kyori.adventure.platform.fabric.FabricServerAudiences
import net.kyori.adventure.platform.fabric.impl.server.ServerBossBarListener
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer

class FabricRattlePlatform(
    private val mod: FabricRattle,
    private val server: MinecraftServer,
) : RattlePlatform<ServerLevel, CommandSourceStack>(mod.rattle) {
    override val worlds: Iterable<ServerLevel>
        get() = server.allLevels

    val audiences = FabricServerAudiences.of(server)
    @Suppress("UnstableApiUsage")
    val bossBars = ServerBossBarListener(audiences)
    val renders = DisplayRenders(audiences)

    override fun callBeforeStep(dt: Real) {
        RattleEvents.BEFORE_STEP.invoker().beforeStep(this, dt)
    }

    override fun asPlayer(sender: CommandSourceStack) =
        (sender.entity as? ServerPlayer)?.let { mod.playerData(it) }

    override fun key(world: ServerLevel) = world.dimension().key()

    override fun physicsOrNull(world: ServerLevel) =
        mod.physicsOrNull(world)

    override fun physicsOrCreate(world: ServerLevel) =
        mod.physicsOrCreate(world)

    fun onTick() {
        tick()
        server.allLevels.forEach { level ->
            physicsOrNull(level)?.withLock { physics ->
                physics.onTick()
            }
        }
    }
}
