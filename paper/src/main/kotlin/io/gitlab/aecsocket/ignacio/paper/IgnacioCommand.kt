package io.gitlab.aecsocket.ignacio.paper

import cloud.commandframework.ArgumentDescription
import cloud.commandframework.arguments.standard.StringArgument
import cloud.commandframework.bukkit.CloudBukkitCapabilities
import cloud.commandframework.execution.CommandExecutionCoordinator
import cloud.commandframework.minecraft.extras.MinecraftHelp
import cloud.commandframework.paper.PaperCommandManager
import io.gitlab.aecsocket.ignacio.core.*
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import kotlin.math.sqrt

private const val ROOT = "ignacio"

private fun desc(text: String) = ArgumentDescription.of(text)

private fun perm(key: String) = "$ROOT.command.$key"

internal class IgnacioCommand(private val ignacio: Ignacio) {
    private val manager = PaperCommandManager(
        ignacio,
        CommandExecutionCoordinator.simpleCoordinator(),
        { it }, { it }
    )
    private val root = manager.commandBuilder(ROOT, desc("Core command plugin."))

    init {
        if (manager.hasCapability(CloudBukkitCapabilities.BRIGADIER))
            manager.registerBrigadier()

        val help = MinecraftHelp("/$ROOT help", { it }, manager)

        manager.command(root
            .literal("help", desc("Lists help information."))
            .argument(StringArgument.optional("query", StringArgument.StringMode.GREEDY))
            .handler { ctx ->
                val query = ctx.getOptional<String>("query").orElse("")
                help.queryCommands(
                    if (query.startsWith("$ROOT ")) query else "$ROOT $query",
                    ctx.sender
                )
            })
        manager.command(root
            .literal("reload", desc("Reloads all plugin data."))
            .permission(perm("reload"))
            .handler { ctx ->
                val sender = ctx.sender
                ignacio.reload()
                sender.sendMessage(Component.text("Reloaded Ignacio."))
            })

        manager.command(root
            .literal("test")
            .handler { ctx ->
                val player = ctx.sender as Player
                val world = player.world

                val physSpace = ignacio.spaceOf(world)
                val r = 1.0 / sqrt(2.0)
                val groundPlane = ignacio.backend.createStaticBody(
                    IgPlaneShape,
                    Transform(Vec3.Zero, Quat(0.0, 0.0, r, r))
                )
                val box = ignacio.backend.createDynamicBody(
                    IgBoxShape(Vec3(0.5)),
                    Transform(Vec3(0.0, 5.0, 0.0), Quat.Identity),
                    IgBodyDynamics(
                        mass = 1.0
                    )
                )
                physSpace.addBody(groundPlane)
                physSpace.addBody(box)
            })
    }
}
