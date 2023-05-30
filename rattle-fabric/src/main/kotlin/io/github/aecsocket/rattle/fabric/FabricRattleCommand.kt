package io.github.aecsocket.rattle.fabric

import cloud.commandframework.context.CommandContext
import cloud.commandframework.fabric.argument.ResourceLocationArgument
import cloud.commandframework.fabric.argument.server.Vec3dArgument
import cloud.commandframework.fabric.data.Coordinates
import io.github.aecsocket.alexandria.fabric.commandManager
import io.github.aecsocket.alexandria.fabric.extension.toDVec
import io.github.aecsocket.rattle.*
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel

internal class FabricRattleCommand(
    rattle: RattleMod,
) : RattleCommand<CommandSourceStack, ServerLevel>(rattle, rattle.messages, commandManager()) {
    override fun locationArgumentOf(key: String) =
        Vec3dArgument.of<CommandSourceStack>(key)

    override fun CommandContext<CommandSourceStack>.getLocation(key: String): Location<ServerLevel> {
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

    override fun CommandContext<CommandSourceStack>.getWorld(key: String): ServerLevel {
        val res = ResourceKey.create(Registries.DIMENSION, get(key))
        return sender.server.getLevel(res) ?: throw IllegalArgumentException("No world with key ${res.key()}")
    }

    override val CommandContext<CommandSourceStack>.server: RattleMod.Server
        get() = sender.server.rattle()
}
