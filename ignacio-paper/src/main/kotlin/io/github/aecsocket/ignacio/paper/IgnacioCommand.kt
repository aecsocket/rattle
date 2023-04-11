package io.github.aecsocket.ignacio.paper

import cloud.commandframework.arguments.standard.DoubleArgument
import cloud.commandframework.arguments.standard.FloatArgument
import cloud.commandframework.arguments.standard.IntegerArgument
import cloud.commandframework.bukkit.parsers.WorldArgument
import cloud.commandframework.bukkit.parsers.location.LocationArgument
import io.github.aecsocket.alexandria.extension.flag
import io.github.aecsocket.alexandria.extension.hasFlag
import io.github.aecsocket.alexandria.paper.BaseCommand
import io.github.aecsocket.alexandria.paper.Context
import io.github.aecsocket.alexandria.paper.ItemDescriptor
import io.github.aecsocket.alexandria.paper.extension.position
import io.github.aecsocket.alexandria.paper.render.ModelDescriptor
import io.github.aecsocket.glossa.messageProxy
import io.github.aecsocket.ignacio.*
import io.github.aecsocket.klam.*
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import kotlin.random.Random

private const val COUNT = "count"
private const val DENSITY = "density"
private const val FRICTION = "friction"
private const val HALF_EXTENT = "half-extent"
private const val LOCATION = "location"
private const val MASS = "mass"
private const val RADIUS = "radius"
private const val RESTITUTION = "restitution"
private const val SHOW_TIMINGS = "show-timings"
private const val SPREAD = "spread"
private const val VIRTUAL = "virtual"
private const val WORLD = "world"

internal class IgnacioCommand(
    private val ignacio: Ignacio
) : BaseCommand(ignacio, ignacio.glossa.messageProxy()) {
    init {
        root.literal("space")
            .argument(WorldArgument.of(WORLD))
            .let { space ->
                manager.command(space
                    .literal("create")
                    .alexandriaPermission("space.create")
                    .alexandriaHandler(::spaceCreate)
                )
                manager.command(space
                    .literal("destroy")
                    .alexandriaPermission("space.destroy")
                    .alexandriaHandler(::spaceDestroy)
                )
            }

        root.literal("body").let { body ->
            body.literal("create")
                .alexandriaPermission("body.create")
                .argument(LocationArgument.of(LOCATION))
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
                ).let { create ->
                    create.literal("static").let { static ->
                        manager.command(static
                            .literal("box")
                            .argument(FloatArgument.of(HALF_EXTENT))
                            .alexandriaHandler(::bodyCreateStaticBox)
                        )
                        manager.command(static
                            .literal("sphere")
                            .argument(FloatArgument.of(RADIUS))
                            .alexandriaHandler(::bodyCreateStaticSphere)
                        )
                    }

                    create.literal("moving")
                        .flag(manager.flagBuilder(FRICTION)
                            .withAliases("f")
                            .withArgument(FloatArgument.builder<CommandSender>(FRICTION)
                                .withMin(0).build())
                        )
                        .flag(manager.flagBuilder(RESTITUTION)
                            .withAliases("r")
                            .withArgument(FloatArgument.builder<CommandSender>(RESTITUTION)
                                .withMin(0).build())
                        )
                        .flag(manager.flagBuilder(MASS)
                            .withAliases("m")
                            .withArgument(FloatArgument.builder<CommandSender>(MASS)
                                .withMin(0).build())
                        )
                        .flag(manager.flagBuilder(DENSITY)
                            .withAliases("d")
                            .withArgument(FloatArgument.builder<CommandSender>(DENSITY)
                                .withMin(0).build())
                        )
                        .let { moving ->
                            manager.command(moving
                                .literal("box")
                                .argument(FloatArgument.of(HALF_EXTENT))
                                .alexandriaHandler(::bodyCreateMovingBox)
                            )
                            manager.command(moving
                                .literal("sphere")
                                .argument(FloatArgument.of(RADIUS))
                                .alexandriaHandler(::bodyCreateMovingSphere)
                            )
                    }
                }

            body.literal("destroy")
                .alexandriaPermission("body.destroy").let { destroy ->
                manager.command(destroy
                    .literal("all")
                    .alexandriaHandler(::bodyDestroyAll)
                )
            }
        }

        manager.command(root
            .literal("timings")
            .alexandriaPermission("timings")
            .alexandriaHandler(::timings)
        )

        manager.command(root
            .literal("debug")
            .alexandriaPermission("debug")
            .senderType(Player::class.java)
            .flag(manager.flagBuilder(SHOW_TIMINGS)
                .withAliases("t")
            )
            .alexandriaHandler(::debug)
        )
    }

    private fun spaceCreate(ctx: Context) {
        val sender = ctx.sender
        val messages = ignacio.messages.forAudience(sender)
        val world = ctx.get<World>(WORLD)

        if (ignacio.worlds.contains(world)) {
            messages.error.space.alreadyExists(
                world = world.name,
            ).sendTo(sender)
            return
        }

        ignacio.worlds.getOrCreate(world)
        messages.command.space.create(
            world = world.name,
        ).sendTo(sender)
    }

    private fun spaceDestroy(ctx: Context) {
        val sender = ctx.sender
        val messages = ignacio.messages.forAudience(sender)
        val world = ctx.get<World>(WORLD)

        if (!ignacio.worlds.contains(world)) {
            messages.error.space.doesNotExist(
                world = world.name,
            ).sendTo(sender)
            return
        }

        ignacio.worlds.destroy(world)
        messages.command.space.destroy(
            world = world.name,
        ).sendTo(sender)
    }

    private fun bodyCreate(
        location: Location,
        count: Int,
        spread: Double,
        virtual: Boolean,
        model: ItemDescriptor,
        scale: FVec3,
        createBody: (physics: PhysicsSpace, position: DVec3, rotation: FQuat) -> PhysicsBody,
    ) {
        val item = model.create()
        repeat(count) {
            val position = location.position() - spread + Random.nextDVec3() * (spread * 2)
            ignacio.primitiveBodies.create(
                location.world,
                position,
                { physics -> createBody(physics, position, FQuat.identity()) },
                if (virtual) null else {
                    { tracker ->
                        ignacio.renders.create(ModelDescriptor(
                            item = item,
                            tracker = tracker,
                            interpolationDuration = 2,
                        ), position, FAffine3(scale = scale))
                    }
                }
            )
        }
    }

    private fun bodyCreateStatic(
        location: Location,
        count: Int,
        spread: Double,
        virtual: Boolean,
        model: ItemDescriptor,
        scale: FVec3,
        geometry: Geometry,
    ) {
        val descriptor = StaticBodyDescriptor(
            shape = ignacio.engine.shape(geometry),
            contactFilter = ignacio.engine.contactFilter(ignacio.engine.layers.moving),
        )
        bodyCreate(location, count, spread, virtual, model, scale) { physics, position, rotation ->
            physics.bodies.createStatic(descriptor, position, rotation)
        }
    }

    private fun bodyCreateStaticBox(ctx: Context) {
        val sender = ctx.sender
        val messages = ignacio.messages.forAudience(sender)
        val location = ctx.get<Location>(LOCATION)
        val halfExtent = ctx.get<Float>(HALF_EXTENT)
        val count = ctx.flag(COUNT) ?: 1
        val spread = ctx.flag(SPREAD) ?: 0.0
        val virtual = ctx.hasFlag(VIRTUAL)

        bodyCreateStatic(
            location, count, spread, virtual,
            ignacio.settings.primitiveBodies.models.box,
            FVec3(halfExtent * 2.0f),
            BoxGeometry(FVec3(halfExtent)),
        )

        messages.command.body.create.static.box(
            count = count,
            locationX = location.x, locationY = location.y, locationZ = location.z,
        ).sendTo(sender)
    }

    private fun bodyCreateStaticSphere(ctx: Context) {
        val sender = ctx.sender
        val messages = ignacio.messages.forAudience(sender)
        val location = ctx.get<Location>(LOCATION)
        val radius = ctx.get<Float>(RADIUS)
        val count = ctx.flag(COUNT) ?: 1
        val spread = ctx.flag(SPREAD) ?: 0.0
        val virtual = ctx.hasFlag(VIRTUAL)

        bodyCreateStatic(
            location, count, spread, virtual,
            ignacio.settings.primitiveBodies.models.sphere,
            FVec3(radius * 2.0f),
            SphereGeometry(radius),
        )

        messages.command.body.create.static.sphere(
            count = count,
            locationX = location.x, locationY = location.y, locationZ = location.z,
        ).sendTo(sender)
    }

    private fun bodyCreateMoving(
        location: Location,
        count: Int,
        spread: Double,
        virtual: Boolean,
        friction: Float,
        restitution: Float,
        mass: Float?,
        model: ItemDescriptor,
        scale: FVec3,
        geometry: Geometry,
    ) {
        val descriptor = MovingBodyDescriptor(
            shape = ignacio.engine.shape(geometry),
            contactFilter = ignacio.engine.contactFilter(ignacio.engine.layers.moving),
            friction = friction,
            restitution = restitution,
            mass = mass?.let { Mass.Constant(it) } ?: Mass.Calculate,
        )
        bodyCreate(location, count, spread, virtual, model, scale) { physics, position, rotation ->
            physics.bodies.createMoving(descriptor, position, rotation)
        }
    }

    private fun bodyCreateMovingBox(ctx: Context) {
        val sender = ctx.sender
        val messages = ignacio.messages.forAudience(sender)
        val location = ctx.get<Location>(LOCATION)
        val halfExtent = ctx.get<Float>(HALF_EXTENT)
        val count = ctx.flag(COUNT) ?: 1
        val spread = ctx.flag(SPREAD) ?: 0.0
        val virtual = ctx.hasFlag(VIRTUAL)
        val friction = ctx.flag(FRICTION) ?: 0.2f
        val restitution = ctx.flag(RESTITUTION) ?: 0.0f
        val mass = ctx.flag<Float>(MASS)
        val density = ctx.flag(DENSITY) ?: DEFAULT_DENSITY

        bodyCreateMoving(
            location, count, spread, virtual,
            friction, restitution, mass,
            ignacio.settings.primitiveBodies.models.box,
            FVec3(halfExtent * 2.0f),
            BoxGeometry(FVec3(halfExtent), density = density),
        )

        messages.command.body.create.moving.box(
            count = count,
            locationX = location.x, locationY = location.y, locationZ = location.z,
        ).sendTo(sender)
    }

    private fun bodyCreateMovingSphere(ctx: Context) {
        val sender = ctx.sender
        val messages = ignacio.messages.forAudience(sender)
        val location = ctx.get<Location>(LOCATION)
        val radius = ctx.get<Float>(RADIUS)
        val count = ctx.flag(COUNT) ?: 1
        val spread = ctx.flag(SPREAD) ?: 0.0
        val virtual = ctx.hasFlag(VIRTUAL)
        val friction = ctx.flag(FRICTION) ?: 0.2f
        val restitution = ctx.flag(RESTITUTION) ?: 0.0f
        val mass = ctx.flag<Float>(MASS)
        val density = ctx.flag(DENSITY) ?: DEFAULT_DENSITY

        bodyCreateMoving(
            location, count, spread, virtual,
            friction, restitution, mass,
            ignacio.settings.primitiveBodies.models.sphere,
            FVec3(radius * 2.0f),
            SphereGeometry(radius, density = density),
        )

        messages.command.body.create.moving.sphere(
            count = count,
            locationX = location.x, locationY = location.y, locationZ = location.z,
        ).sendTo(sender)
    }

    private fun bodyDestroyAll(ctx: Context) {
        val sender = ctx.sender
        val messages = ignacio.messages.forAudience(sender)

        val count = ignacio.primitiveBodies.count
        ignacio.primitiveBodies.destroyAll()

        messages.command.body.destroy.all(
            count = count,
        ).sendTo(sender)
    }

    private fun timings(ctx: Context) {
        val sender = ctx.sender
        val messages = ignacio.messages.forAudience(sender)

        messages.command.timings.timingsHeader().sendTo(sender)

        ignacio.settings.engineTimings.buffersToDisplay.forEach { buffer ->
            val (median, best5, worst5) = timingStatsOf(ignacio.engineTimings.getLast((buffer * 1000).toLong()))
            messages.command.timings.timing(
                buffer = buffer,
                median = formatTiming(median, messages),
                best5 = formatTiming(best5, messages),
                worst5 = formatTiming(worst5, messages),
            ).sendTo(sender)
        }

        messages.command.timings.spacesHeader(
            numWorldPhysicsSpaces = ignacio.worlds.count,
        ).sendTo(sender)

        ignacio.worlds.all().forEach { (_, world) ->
            messages.command.timings.space(
                worldName = world.world.name,
                numBodies = world.physics.bodies.count,
                numActiveBodies = world.physics.bodies.activeCount,
            ).sendTo(sender)
        }
    }

    private fun debug(ctx: Context) {
        val sender = ctx.sender as Player
        val showTimings = ctx.hasFlag(SHOW_TIMINGS)

        ignacio.playerData(sender).updateDebugFlags(IgnacioPlayer.DebugFlags(
            showTimings = showTimings,
        ))
    }
}
