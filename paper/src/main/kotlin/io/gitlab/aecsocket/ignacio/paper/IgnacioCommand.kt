package io.gitlab.aecsocket.ignacio.paper

import cloud.commandframework.ArgumentDescription
import cloud.commandframework.arguments.standard.BooleanArgument
import cloud.commandframework.arguments.standard.IntegerArgument
import cloud.commandframework.arguments.standard.StringArgument
import cloud.commandframework.bukkit.CloudBukkitCapabilities
import cloud.commandframework.bukkit.parsers.location.LocationArgument
import cloud.commandframework.execution.CommandExecutionCoordinator
import cloud.commandframework.minecraft.extras.MinecraftHelp
import cloud.commandframework.paper.PaperCommandManager
import io.gitlab.aecsocket.ignacio.bullet.nextVec3
import io.gitlab.aecsocket.ignacio.core.*
import io.gitlab.aecsocket.ignacio.core.math.Quat
import io.gitlab.aecsocket.ignacio.core.math.Transform
import io.gitlab.aecsocket.ignacio.core.math.Vec3
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
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

    data class Box(
        val space: IgPhysicsSpace,
        val body: IgDynamicBody,
        val mesh: IgMesh,
    )

    val boxes = ArrayList<Box>()

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
            .handler { ctx ->
                val sender = ctx.sender
                ignacio.reload()
                sender.sendMessage(text("Reloaded Ignacio.", cP2))
            })
        manager.command(root
            .literal("stats", desc("Show physics engine statistics."))
            .permission(perm("stats"))
            .handler { ctx ->
                val sender = ctx.sender

                data class WorldData(
                    val worldName: String,
                    val physSpace: IgPhysicsSpace?
                ) {
                    var bodiesAwake = -1
                    var bodiesTotal = -1
                }

                val worlds = Bukkit.getWorlds().map { world ->
                    WorldData(world.name, ignacio.spaceOfOrNull(world))
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
            })
        manager.command(root
            .literal("stats", desc("Show physics engine statistics."))
            .argument(BooleanArgument.of("show"), desc("If stats should be shown in the boss bar."))
            .permission(perm("stats"))
            .senderType(Player::class.java)
            .handler { ctx ->
                val player = ctx.sender as Player

            })

        manager.command(root
            .literal("create")
            .argument(LocationArgument.of("location"))
            .argument(IntegerArgument.of("amount"))
            .argument(LocationArgument.of("spread"))
            .flag(manager.flagBuilder("virtual")
                .withAliases("v"))
            .handler { ctx ->
                val player = ctx.sender as Player
                val location = ctx.get<Location>("location")
                val amount = ctx.get<Int>("amount")
                val spread = ctx.get<Location>("spread").vec3() * 2.0
                val virtual = ctx.flags().hasFlag("virtual")

                val world = location.world
                val from = location.vec3() - (spread / 2.0)

                data class NewBox(
                    val pos: Vec3,
                    val mesh: IgMesh
                )

                val newBoxes = ArrayList<NewBox>()

                val tracker =
                    if (virtual) IgPlayerTracker { emptySet() }
                    else IgPlayerTracker { setOf(player) }
                repeat(amount) {
                    val pos = from + Random.nextVec3() * spread
                    val mesh = ignacio.meshes.createItem(
                        Transform(pos, Quat.Identity),
                        tracker,
                        IgMesh.Settings(interpolate = false, small = true),
                        ItemStack(Material.STICK).apply {
                            editMeta { meta ->
                                meta.setCustomModelData(2)
                            }
                        }
                    )
                    mesh.spawn()
                    newBoxes.add(NewBox(pos, mesh))
                }

                ignacio.executePhysics {
                    val physSpace = ignacio.spaceOf(world)

                    newBoxes.forEach { box ->
                        val body = ignacio.backend.createDynamicBody(
                            IgBoxShape(Vec3(0.5)),
                            Transform(box.pos, Quat.Identity),
                            IgBodyDynamics(
                                mass = 1.0
                            )
                        )
                        physSpace.addBody(body)

                        boxes.add(Box(physSpace, body, box.mesh))
                    }
                }
            })
        manager.command(root
            .literal("remove")
            .handler { ctx ->
                boxes.forEach { box ->
                    ignacio.executePhysics {
                        box.space.removeBody(box.body)
                        box.body.destroy()
                    }
                    box.mesh.despawn()
                }
                boxes.clear()
            })

        Bukkit.getScheduler().scheduleSyncRepeatingTask(ignacio, {
            ignacio.executePhysics {
                boxes.forEach { box ->
                    box.mesh.transform = box.body.transform
                }
            }
        }, 0, 1)
    }
}
