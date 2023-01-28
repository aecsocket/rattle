package io.gitlab.aecsocket.ignacio.paper

import cloud.commandframework.ArgumentDescription
import cloud.commandframework.arguments.standard.DoubleArgument
import cloud.commandframework.arguments.standard.IntegerArgument
import cloud.commandframework.arguments.standard.StringArgument
import cloud.commandframework.bukkit.CloudBukkitCapabilities
import cloud.commandframework.bukkit.parsers.location.LocationArgument
import cloud.commandframework.context.CommandContext
import cloud.commandframework.execution.CommandExecutionCoordinator
import cloud.commandframework.minecraft.extras.MinecraftHelp
import cloud.commandframework.paper.PaperCommandManager
import io.gitlab.aecsocket.ignacio.bullet.nextVec3
import io.gitlab.aecsocket.ignacio.core.*
import io.gitlab.aecsocket.ignacio.core.math.Vec3
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import kotlin.random.Random

private const val ROOT = "ignacio"

private fun desc(text: String) = ArgumentDescription.of(text)

private fun perm(key: String) = "$ROOT.command.$key"

private operator fun Component.plus(c: Component) = this.append(c)

private val cP1 = NamedTextColor.WHITE
private val cP2 = NamedTextColor.GRAY
private val cP3 = NamedTextColor.DARK_GRAY
private val cErr = NamedTextColor.RED

private val timingColors = mapOf(
    50.0 to NamedTextColor.RED,
    15.0 to NamedTextColor.YELLOW,
    0.0 to NamedTextColor.GREEN
)

private fun textTiming(ms: Double): Component {
    val color = timingColors.firstNotNullOf { (thresh, color) ->
        if (ms > thresh) color else null
    }
    return text("%.2fms".format(ms), color)
}

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
            .literal("stats", desc("Show physics engine statistics."))
            .permission(perm("stats"))
            .handler(::stats))
        manager.command(root
            .literal("render", desc("Renders body debug info through particles."))
            .flag(manager.flagBuilder("com")
                .withAliases("c")
                .withDescription(desc("Body center of mass.")))
            .flag(manager.flagBuilder("velocity")
                .withAliases("v")
                .withDescription(desc("Body linear velocity.")))
            .flag(manager.flagBuilder("shape")
                .withAliases("s")
                .withDescription(desc("Body shape.")))
            .permission(perm("render"))
            .senderType(Player::class.java)
            .handler(::render))

        val primitive = root
            .literal("primitives", desc("Manage primitive debug bodies."))
        val primitiveCreate = primitive
            .literal("create", desc("Create a primitive body."))

        val primitiveCreateLocation = LocationArgument.builder<CommandSender>("location")
            .withDefaultDescription(desc("Where to create the body."))
        val primitiveCreateVisual = manager.flagBuilder("visual")
            .withAliases("v")
            .withDescription(desc("Body will have a visible debug mesh attached to it."))
            .build()
        val primitiveCreateMass = manager.flagBuilder("mass")
            .withAliases("m")
            .withArgument(DoubleArgument.of<CommandSender>("mass"))
            .withDescription(desc("Mass of body in kilograms."))
            .build()
        val primitiveCreateCount = manager.flagBuilder("count")
            .withAliases("n")
            .withArgument(IntegerArgument.of<CommandSender>("count"))
            .withDescription(desc("Number of bodies to spawn."))
            .build()
        val primitiveCreateSpread = manager.flagBuilder("spread")
            .withAliases("s")
            .withArgument(DoubleArgument.of<CommandSender>("spread"))
            .withDescription(desc("Max random half-extent to offset each body by."))
            .build()

        manager.command(primitiveCreate
            .literal("sphere", desc("Create a sphere body."))
            .argument(DoubleArgument.of("radius"), desc("Radius of the sphere."))
            .argument(primitiveCreateLocation)
            .flag(primitiveCreateVisual)
            .flag(primitiveCreateMass)
            .flag(primitiveCreateCount)
            .flag(primitiveCreateSpread)
            .permission(perm("primitive.create"))
            .handler(::primitiveCreateSphere))
        manager.command(primitiveCreate
            .literal("box", desc("Create a box body."))
            .argument(DoubleArgument.of("half-extent"), desc("Radius of the sphere."))
            .argument(primitiveCreateLocation)
            .flag(primitiveCreateVisual)
            .flag(primitiveCreateMass)
            .flag(primitiveCreateCount)
            .flag(primitiveCreateSpread)
            .permission(perm("primitive.create"))
            .handler(::primitiveCreateBox))
        manager.command(primitive
            .literal("remove", desc("Remove all primitive debug bodies."))
            .permission(perm("primitive.remove"))
            .handler(::primitiveRemove))
    }

    private fun reload(ctx: CommandContext<CommandSender>) {
        val sender = ctx.sender
        ignacio.reload()
        sender.sendMessage(text("Reloaded Ignacio.", cP2))
    }

    private fun stats(ctx: CommandContext<CommandSender>) {
        val sender = ctx.sender

        data class WorldData(
            val worldName: String,
            val physSpace: IgPhysicsSpace?
        ) {
            var bodiesAwake = -1
            var bodiesTotal = -1
        }

        val worlds = Bukkit.getWorlds().map { world ->
            WorldData(world.name, ignacio.physicsSpaceOfOrNull(world))
        }

        sender.sendMessage(text("Fetching...", cP3))

        ignacio.runAsync {
            worlds.map { data -> launch {
                val physSpace = data.physSpace ?: return@launch
                data.bodiesAwake = ignacio.runPhysics { physSpace.countBodies(onlyAwake = true) }
                data.bodiesTotal = ignacio.runPhysics { physSpace.countBodies() }
            } }.joinAll()

            worlds.forEach { data ->
                data.physSpace ?: run {
                    sender.sendMessage(
                        text("World '", cErr)
                        + text(data.worldName, cP1)
                        + text("' has no physics space", cErr)
                    )
                    return@forEach
                }

                sender.sendMessage(
                    text("Stats for physics space of '", cP2)
                    + text(data.worldName, cP1)
                    + text("':", cP2)
                )
                sender.sendMessage(
                    text("  Bodies: ", cP2)
                    + text(data.bodiesAwake, cP1)
                    + text(" awake / ", cP2)
                    + text(data.bodiesTotal, cP1)
                    + text(" total", cP2)
                )
            }

            sender.sendMessage(text("Step timings from the last...", cP2))
            ignacio.settings.stepTimeIntervals.forEach { interval ->
                val times = ignacio.lastStepTimes.getLast((interval * 1000).toLong())
                    .sorted()
                val avg = times.average() / 1.0e6
                // val min = times.first() / 1.0e6
                // val max = times.last() / 1.0e6
                val top5 = times[(times.size * 0.95).toInt()] / 1.0e6
                val bottom5 = times[(times.size * 0.05).toInt()] / 1.0e6
                sender.sendMessage(
                    text(" · ", cP2)
                    + text("%.1fs".format(interval), cP1)
                    + text(": ", cP2)
                    + textTiming(avg)
                    + text(" avg / ", cP2)
                    + textTiming(bottom5)
                    + text(" 5%ile / ", cP2)
                    + textTiming(top5)
                    + text(" 95%ile", cP2)
                )
            }
        }
    }

    private fun render(ctx: CommandContext<CommandSender>) {
        val player = ctx.sender as Player
        val com = ctx.flags().hasFlag("com")
        val velocity = ctx.flags().hasFlag("velocity")
        val shape = ctx.flags().hasFlag("shape")

        ignacio.playerRenderSettings[player] = RenderSettings(
            centerOfMass = com,
            linearVelocity = velocity,
            shape = shape
        )
    }

    private fun primitiveCreate(ctx: CommandContext<CommandSender>, geometry: IgGeometry) {
        val location = ctx.get<Location>("location")
        val visual = ctx.flags().hasFlag("visual")
        val mass = ctx.flags().getValue<Double>("mass").orElse(1.0)
        val count = ctx.flags().getValue<Int>("count").orElse(1)
        val spread = ctx.flags().getValue<Double>("spread").orElse(0.0)

        val world = location.world
        val min = location.vec3() - spread
        val spread2 = spread * 2.0

        repeat(count) {
            val position = min + Random.nextVec3() * spread2
            ignacio.primitives.create(position.location(world), geometry, mass, visual)
        }
    }

    private fun primitiveCreateSphere(ctx: CommandContext<CommandSender>) {
        val radius = ctx.get<Double>("radius")
        primitiveCreate(ctx, IgSphereGeometry(radius))
    }

    private fun primitiveCreateBox(ctx: CommandContext<CommandSender>) {
        val halfExtent = ctx.get<Double>("half-extent")
        primitiveCreate(ctx, IgBoxGeometry(Vec3(halfExtent)))
    }

    private fun primitiveRemove(ctx: CommandContext<CommandSender>) {
        val sender = ctx.sender
        val count = ignacio.primitives.countPrimitives()
        ignacio.primitives.removeAll()
        sender.sendMessage(
            text("Removed ", cP2)
            + text(count, cP1)
            + text(" primitive bodies.", cP2)
        )
    }
}
