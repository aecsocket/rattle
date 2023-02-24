package io.github.aecsocket.ignacio.paper

import cloud.commandframework.arguments.standard.BooleanArgument
import cloud.commandframework.arguments.standard.DoubleArgument
import cloud.commandframework.arguments.standard.FloatArgument
import cloud.commandframework.arguments.standard.IntegerArgument
import cloud.commandframework.bukkit.parsers.location.LocationArgument
import io.github.aecsocket.alexandria.core.extension.flag
import io.github.aecsocket.alexandria.core.extension.getOr
import io.github.aecsocket.alexandria.core.extension.hasFlag
import io.github.aecsocket.alexandria.core.extension.senderType
import io.github.aecsocket.alexandria.paper.AlexandriaApiCommand
import io.github.aecsocket.alexandria.paper.Context
import io.github.aecsocket.alexandria.paper.ItemDescriptor
import io.github.aecsocket.glossa.core.component
import io.github.aecsocket.glossa.core.messageProxy
import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.math.Transform
import io.github.aecsocket.ignacio.core.math.Vec3f
import io.github.aecsocket.ignacio.core.math.nextVec3d
import io.github.aecsocket.ignacio.paper.util.position
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import kotlin.math.max
import kotlin.random.Random

private const val COUNT = "count"
private const val HALF_EXTENT = "half-extent"
private const val LOCATION = "location"
private const val MASS = "mass"
private const val RADIUS = "radius"
private const val SPREAD = "spread"
private const val VIRTUAL = "virtual"
private const val DEFAULT_HALF_EXTENT = 0.5f
private const val DEFAULT_RADIUS = 0.5f
private val timeColors = mapOf(
    50.0 to NamedTextColor.RED,
    15.0 to NamedTextColor.YELLOW,
    0.0 to NamedTextColor.GREEN,
)

internal class IgnacioCommand(
    private val ignacio: Ignacio
) : AlexandriaApiCommand(ignacio, ignacio.glossa.messageProxy()) {
    init {
        root.literal("timings").let { timings ->
            manager.command(timings
                .senderType(Player::class)
                .alexandriaPermission("timings")
                .handler(::timings)
            )
            manager.command(timings
                .argument(BooleanArgument.of("display"))
                .senderType(Player::class)
                .alexandriaPermission("timings")
                .handler(::timingsDisplay)
            )
        }
        root.literal("primitives").let { primitives ->
            primitives.literal("create")
                .flag(manager.flagBuilder(COUNT)
                    .withAliases("n")
                    .withArgument(IntegerArgument.builder<CommandSender>(COUNT)
                        .withMin(1).build())
                )
                .flag(manager.flagBuilder(SPREAD)
                    .withAliases("s")
                    .withArgument(DoubleArgument.builder<CommandSender>(SPREAD)
                        .withMin(0).build())
                )
                .flag(manager.flagBuilder(VIRTUAL)
                    .withAliases("v")
                )
                .argument(LocationArgument.of(LOCATION)).let { create ->
                    create.literal("static").let { static ->
                        manager.command(static
                            .literal("box")
                            .argument(FloatArgument.optional(HALF_EXTENT))
                            .alexandriaPermission("primitives.create")
                            .handler(::primitivesCreateStaticBox)
                        )
                        manager.command(static
                            .literal("sphere")
                            .argument(FloatArgument.optional(RADIUS))
                            .alexandriaPermission("primitives.create")
                            .handler(::primitivesCreateStaticSphere)
                        )
                    }

                    create.literal("dynamic")
                        .flag(manager.flagBuilder(MASS)
                            .withAliases("m")
                            .withArgument(FloatArgument.builder<CommandSender>(MASS)
                                .withMin(0).build())
                        )
                        .let { dynamic ->
                            manager.command(dynamic
                                .literal("box")
                                .argument(FloatArgument.optional(HALF_EXTENT))
                                .alexandriaPermission("primitives.create")
                                .handler(::primitivesCreateDynamicBox)
                            )
                            manager.command(dynamic
                                .literal("sphere")
                                .argument(FloatArgument.optional(RADIUS))
                                .alexandriaPermission("primitives.create")
                                .handler(::primitivesCreateDynamicSphere)
                            )
                        }
                }
            manager.command(primitives
                .literal("remove")
                .alexandriaPermission("primitives.remove")
                .handler(::primitivesRemove)
            )
        }
    }

    private fun primitivesCreate(
        count: Int,
        spread: Double,
        virtual: Boolean,
        origin: Location,
        model: ItemDescriptor,
        addBody: (physics: PhysicsSpace, transform: Transform) -> PhysicsBody,
    ) {
        repeat(count) {
            val transform = Transform(origin.position() - spread + Random.nextVec3d() * (spread*2))
            ignacio.primitiveBodies.create(
                world = origin.world,
                transform = transform,
                createBody = { addBody(it, transform) },
                createRender = if (virtual) null else { { ignacio.renders.createModel(it, transform, model.create()) } },
            )
        }
    }

    private fun primitivesCreateStatic(
        count: Int,
        spread: Double,
        virtual: Boolean,
        origin: Location,
        model: ItemDescriptor,
        geometry: Geometry,
    ) {
        primitivesCreate(count, spread, virtual, origin, model) { physics, transform ->
            physics.addStaticBody(geometry, transform)
        }
    }

    private fun primitivesCreateDynamic(
        count: Int,
        mass: Float,
        spread: Double,
        virtual: Boolean,
        origin: Location,
        model: ItemDescriptor,
        geometry: Geometry,
    ) {
        val dynamics = BodyDynamics(
            activate = true,
            mass = mass,
        )
        primitivesCreate(count, spread, virtual, origin, model) { physics, transform ->
            physics.addDynamicBody(geometry, transform, dynamics)
        }
    }

    private fun primitivesCreateStaticBox(ctx: Context) {
        val sender = ctx.sender
        val messages = ignacio.messages.forAudience(sender)
        val location = ctx.get<Location>(LOCATION)
        val halfExtent = ctx.getOr(HALF_EXTENT) ?: DEFAULT_HALF_EXTENT
        val count = ctx.flag(COUNT) ?: 1
        val spread = ctx.flag(SPREAD) ?: 0.0
        val virtual = ctx.hasFlag(VIRTUAL)

        primitivesCreateStatic(
            count, spread, virtual, location,
            ignacio.settings.primitiveModels.box,
            BoxGeometry(Vec3f(halfExtent))
        )

        messages.command.primitives.create.static.box(
            count = count,
            locationX = location.x, locationY = location.y, locationZ = location.z,
        ).sendTo(sender)
    }

    private fun primitivesCreateStaticSphere(ctx: Context) {
        val sender = ctx.sender
        val messages = ignacio.messages.forAudience(sender)
        val location = ctx.get<Location>(LOCATION)
        val radius = ctx.getOr(RADIUS) ?: DEFAULT_RADIUS
        val count = ctx.flag(COUNT) ?: 1
        val spread = ctx.flag(SPREAD) ?: 0.0
        val virtual = ctx.hasFlag(VIRTUAL)

        primitivesCreateStatic(
            count, spread, virtual, location,
            ignacio.settings.primitiveModels.sphere,
            SphereGeometry(radius)
        )

        messages.command.primitives.create.static.sphere(
            count = count,
            locationX = location.x, locationY = location.y, locationZ = location.z,
        ).sendTo(sender)
    }

    private fun primitivesCreateDynamicBox(ctx: Context) {
        val sender = ctx.sender
        val messages = ignacio.messages.forAudience(sender)
        val location = ctx.get<Location>(LOCATION)
        val halfExtent = ctx.getOr(HALF_EXTENT) ?: DEFAULT_HALF_EXTENT
        val count = ctx.flag(COUNT) ?: 1
        val mass = ctx.flag(MASS) ?: 1f
        val spread = ctx.flag(SPREAD) ?: 0.0
        val virtual = ctx.hasFlag(VIRTUAL)

        primitivesCreateDynamic(
            count, mass, spread, virtual, location,
            ignacio.settings.primitiveModels.box,
            BoxGeometry(Vec3f(halfExtent))
        )

        messages.command.primitives.create.dynamic.box(
            count = count,
            mass = mass,
            locationX = location.x, locationY = location.y, locationZ = location.z,
        ).sendTo(sender)
    }

    private fun primitivesCreateDynamicSphere(ctx: Context) {
        val sender = ctx.sender
        val messages = ignacio.messages.forAudience(sender)
        val location = ctx.get<Location>(LOCATION)
        val radius = ctx.getOr(RADIUS) ?: DEFAULT_RADIUS
        val count = ctx.flag(COUNT) ?: 1
        val mass = ctx.flag(MASS) ?: 1f
        val spread = ctx.flag(SPREAD) ?: 0.0
        val virtual = ctx.hasFlag(VIRTUAL)

        primitivesCreateDynamic(
            count, mass, spread, virtual, location,
            ignacio.settings.primitiveModels.sphere,
            SphereGeometry(radius)
        )

        messages.command.primitives.create.dynamic.sphere(
            count = count,
            mass = mass,
            locationX = location.x, locationY = location.y, locationZ = location.z,
        ).sendTo(sender)
    }

    private fun primitivesRemove(ctx: Context) {
        val sender = ctx.sender
        val messages = ignacio.messages.forAudience(sender)

        val count = ignacio.primitiveBodies.numBodies()
        ignacio.primitiveBodies.removeAll()

        messages.command.primitives.remove(
            count = count
        ).sendTo(sender)
    }

    private fun formatTime(time: Double, messages: IgnacioMessages): Component {
        val text = messages.command.timings.time(time).component()
        val clampedTime = max(0.0, time)
        val color = timeColors.firstNotNullOf { (threshold, color) ->
            if (clampedTime >= threshold) color else null
        }
        return text.applyFallbackStyle(color)
    }

    private fun timings(ctx: Context) {
        val sender = ctx.sender
        val messages = ignacio.messages.forAudience(sender)

        messages.command.timings.timingHeader().sendTo(sender)

        ignacio.settings.engineTimings.buffersToDisplay.forEach { buffer ->
            val bufferTimes = ignacio.engineTimings.getLast((buffer * 1000).toLong()).sorted()
            val median: Double
            val best5: Double
            val worst5: Double
            if (bufferTimes.isEmpty()) {
                median = 0.0
                best5 = 0.0
                worst5 = 0.0
            } else {
                fun Long.ms() = this / 1.0e6
                median = bufferTimes[(bufferTimes.size * 0.5).toInt()].ms()
                best5 = bufferTimes[(bufferTimes.size * 0.05).toInt()].ms()
                worst5 = bufferTimes[(bufferTimes.size * 0.95).toInt()].ms()
            }

            messages.command.timings.timing(
                buffer = buffer,
                median = formatTime(median, messages),
                best5 = formatTime(best5, messages),
                worst5 = formatTime(worst5, messages),
            ).sendTo(sender)
        }

        messages.command.timings.spaceHeader(
            numWorldPhysicsSpaces = ignacio.worldPhysics.size,
        ).sendTo(sender)

        ignacio.worldPhysics.forEach { (world, physics) ->
            messages.command.timings.space(
                worldName = world.name,
                numBodies = physics.numBodies,
                numActiveBodies = physics.numActiveBodies,
            ).sendTo(sender)
        }
    }

    private fun timingsDisplay(ctx: Context) {
        val sender = ctx.sender
        val messages = ignacio.messages.forAudience(sender)

    }
}
