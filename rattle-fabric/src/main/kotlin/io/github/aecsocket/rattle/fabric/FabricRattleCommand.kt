package io.github.aecsocket.rattle.fabric

import cloud.commandframework.arguments.CommandArgument
import cloud.commandframework.context.CommandContext
import cloud.commandframework.fabric.FabricCommandContextKeys
import cloud.commandframework.fabric.argument.EntityAnchorArgument
import cloud.commandframework.fabric.argument.RegistryEntryArgument
import cloud.commandframework.fabric.argument.ResourceLocationArgument
import cloud.commandframework.fabric.argument.server.Vec3dArgument
import cloud.commandframework.fabric.data.Coordinates
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.github.aecsocket.alexandria.fabric.commandManager
import io.github.aecsocket.alexandria.fabric.extension.toDVec
import io.github.aecsocket.rattle.Location
import io.github.aecsocket.rattle.RattleCommand
import io.github.aecsocket.rattle.RigidBody
import io.github.aecsocket.rattle.World
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions
import net.fabricmc.fabric.api.event.registry.RegistryAttributeHolder
import net.fabricmc.fabric.impl.registry.sync.FabricRegistryInit
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.dedicated.DedicatedServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.dimension.LevelStem

internal class FabricRattleCommand(
     rattle: Rattle,
) : RattleCommand<CommandSourceStack>(rattle, commandManager()) {
    // TODO there's got to be a Paper/LocationArgument-like arg for Fabric??
    override fun locationArgumentOf(key: String) =
        Vec3dArgument.of<CommandSourceStack>(key)

    override fun CommandContext<CommandSourceStack>.getLocation(key: String): Location {
        val vec = get<Coordinates>(key)
        return Location(
            world = wrap(sender.level),
            position = vec.position().toDVec(),
        )
    }

    // we can't actually use a RegistryEntryArgument since that would give us either:
    // · a Level, which is actually a LevelStem and causes a class cast exception in Cloud internals
    // · a LevelStem, which we can't get the level key from
    // so we manually get the ResourceLocation of our Level
    override fun worldArgumentOf(key: String) =
        ResourceLocationArgument.builder<CommandSourceStack>(key)
            .withSuggestionsProvider { ctx, _ ->
                ctx.sender.server.allLevels.map { level ->
                    level.dimension().location().asString()
                }
            }
            .build()

    override fun CommandContext<CommandSourceStack>.getWorld(key: String): World {
        val res = ResourceKey.create(Registries.DIMENSION, get(key))
        val level = sender.server.getLevel(res) ?: throw IllegalArgumentException("No world with key ${res.key()}")
        return wrap(level)
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
