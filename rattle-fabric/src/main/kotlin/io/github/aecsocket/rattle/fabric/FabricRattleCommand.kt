package io.github.aecsocket.rattle.fabric

import cloud.commandframework.context.CommandContext
import cloud.commandframework.fabric.argument.ResourceLocationArgument
import cloud.commandframework.fabric.argument.server.Vec3dArgument
import cloud.commandframework.fabric.data.Coordinates
import io.github.aecsocket.alexandria.fabric.commandManager
import io.github.aecsocket.alexandria.fabric.extension.toDVec
import io.github.aecsocket.rattle.Location
import io.github.aecsocket.rattle.RattleCommand
import io.github.aecsocket.rattle.RigidBody
import io.github.aecsocket.rattle.WorldPhysics
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level

internal class FabricRattleCommand(
     rattle: Rattle,
) : RattleCommand<CommandSourceStack, Level>(rattle, commandManager()) {
    override fun locationArgumentOf(key: String) =
        Vec3dArgument.of<CommandSourceStack>(key)

    override fun CommandContext<CommandSourceStack>.getLocation(key: String): Location<Level> {
        val vec = get<Coordinates>(key)
        return Location(
            world = sender.level,
            position = vec.position().toDVec(),
        )
    }

    // we can't actually use a RegistryEntryArgument since that would give us either:
    // · a Level, which is actually a LevelStem and causes a class cast exception in Cloud internals
    // · a LevelStem, which we can't get the level key from
    // ...so we manually get the ResourceLocation of our Level
    override fun worldArgumentOf(key: String) =
        ResourceLocationArgument.builder<CommandSourceStack>(key)
            .withSuggestionsProvider { ctx, _ ->
                ctx.sender.server.allLevels.map { level ->
                    level.dimension().location().asString()
                }
            }
            .build()

    override fun CommandContext<CommandSourceStack>.getWorld(key: String): Level {
        val res = ResourceKey.create(Registries.DIMENSION, get(key))
        return sender.server.getLevel(res) ?: throw IllegalArgumentException("No world with key ${res.key()}")
    }

    override fun CommandContext<CommandSourceStack>.worlds(): List<WorldPhysics<Level>> {
        return sender.server.allLevels
            .mapNotNull { level -> level.physicsOrNull() }
    }

    override fun todoBodyStuff(sender: CommandSourceStack, body: RigidBody) {
        val player = sender.entity as ServerPlayer
        // TODO WOW THIS IS STUPID!!!!
        Thread {
            while (true) {
                val pos = body.readBody { rb ->
                    rb.position
                }.translation
                sender.level.sendParticles(player, ParticleTypes.END_ROD, true, pos.x, pos.y, pos.z, 0, 0.0, 0.0, 0.0, 0.0)
                Thread.sleep(50)
            }
        }.start()
    }
}
