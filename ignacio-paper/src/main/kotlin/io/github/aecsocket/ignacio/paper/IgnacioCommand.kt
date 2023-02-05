package io.github.aecsocket.ignacio.paper

import cloud.commandframework.ArgumentDescription
import cloud.commandframework.arguments.standard.StringArgument
import cloud.commandframework.bukkit.CloudBukkitCapabilities
import cloud.commandframework.context.CommandContext
import cloud.commandframework.execution.CommandExecutionCoordinator
import cloud.commandframework.minecraft.extras.MinecraftHelp
import cloud.commandframework.paper.PaperCommandManager
import io.github.aecsocket.ignacio.core.BoxGeometry
import io.github.aecsocket.ignacio.core.math.Transform
import io.github.aecsocket.ignacio.core.math.Vec3d
import io.github.aecsocket.ignacio.core.math.Vec3f
import io.github.aecsocket.ignacio.paper.util.runRepeating
import io.github.aecsocket.ignacio.paper.util.vec
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

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
            .literal("help", desc("List help information."))
            .argument(StringArgument.optional("query", StringArgument.StringMode.GREEDY))
            .handler { ctx ->
                val query = ctx.getOptional<String>("query").orElse("")
                help.queryCommands(
                    if (query.startsWith("$ROOT ")) query else "$ROOT $query",
                    ctx.sender
                )
            })
        manager.command(root
            .literal("reload", desc("Reload all plugin data."))
            .permission(perm("reload"))
            .handler(::reload))

        manager.command(root
            .literal("test")
            .handler { ctx ->
                val player = ctx.sender as Player
                val transform = Transform(player.location.vec())

                val physicsSpace = ignacio.physicsSpaceOf(player.world)
                val box = physicsSpace.addDynamicBody(BoxGeometry(Vec3f(0.5f)), transform)
                val boxMesh = ignacio.meshes.createItem(
                    transform,
                    { setOf(player) },
                    StandMesh.Settings(
                        interpolate = false,
                        small = true
                    ),
                    ItemStack(Material.STICK).apply {
                        editMeta { meta ->
                            meta.setCustomModelData(2)
                        }
                    }
                )
                boxMesh.spawn()

                ignacio.runRepeating {
                    boxMesh.transform = box.transform
                }
            })
    }

    private fun reload(ctx: CommandContext<CommandSender>) {

    }
}
