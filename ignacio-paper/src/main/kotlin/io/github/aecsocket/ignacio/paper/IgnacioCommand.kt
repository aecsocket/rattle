package io.github.aecsocket.ignacio.paper

import cloud.commandframework.arguments.standard.DoubleArgument
import cloud.commandframework.arguments.standard.EnumArgument
import cloud.commandframework.arguments.standard.FloatArgument
import cloud.commandframework.arguments.standard.IntegerArgument
import cloud.commandframework.arguments.standard.StringArgument
import cloud.commandframework.bukkit.data.ProtoItemStack
import cloud.commandframework.bukkit.parsers.ItemStackArgument
import cloud.commandframework.bukkit.parsers.WorldArgument
import cloud.commandframework.bukkit.parsers.location.LocationArgument
import io.github.aecsocket.alexandria.extension.flag
import io.github.aecsocket.alexandria.extension.hasFlag
import io.github.aecsocket.alexandria.paper.BaseCommand
import io.github.aecsocket.alexandria.paper.Context
import io.github.aecsocket.alexandria.paper.ItemDescriptor
import io.github.aecsocket.glossa.messageProxy
import io.github.aecsocket.ignacio.*
import io.github.aecsocket.ignacio.paper.render.ModelDescriptor
import io.github.aecsocket.ignacio.paper.render.RenderDescriptor
import io.github.aecsocket.ignacio.paper.render.TextDescriptor
import io.github.aecsocket.klam.*
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Display.Billboard
import org.bukkit.entity.Player
import kotlin.random.Random

private const val ANGLES = "angles"
private const val BILLBOARD = "billboard"
private const val COUNT = "count"
private const val DENSITY = "density"
private const val FRICTION = "friction"
private const val HALF_EXTENT = "half-extent"
private const val ID = "id"
private const val ITEM = "item"
private const val LOCATION = "location"
private const val MASS = "mass"
private const val ORDER = "order"
private const val RADIUS = "radius"
private const val RESTITUTION = "restitution"
private const val SCALE = "scale"
private const val SHOW_TIMINGS = "show-timings"
private const val SPREAD = "spread"
private const val TEXT = "text"
private const val TO = "to"
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

        root.literal("render").let { render ->
            render.literal("create")
                .alexandriaPermission("render.create")
                .argument(LocationArgument.of(LOCATION))
                .flag(manager.flagBuilder(SCALE)
                    .withAliases("s")
                    .withArgument(FloatArgument.of<CommandSender>(SCALE))
                )
                .let { create ->
                    manager.command(create
                        .literal("model")
                        .argument(ItemStackArgument.of(ITEM))
                        .alexandriaHandler(::renderCreateModel)
                    )
                    manager.command(create
                        .literal("text")
                        .argument(StringArgument.quoted(TEXT))
                        .flag(manager.flagBuilder(BILLBOARD)
                            .withAliases("b")
                            .withArgument(EnumArgument.of<CommandSender, Billboard>(Billboard::class.java, BILLBOARD))
                        )
                        .alexandriaHandler(::renderCreateText)
                    )
                }

            render.literal("destroy")
                .alexandriaPermission("render.destroy").let { destroy ->
                    manager.command(destroy
                        .argument(IntegerArgument.of(ID))
                        .alexandriaHandler(::renderDestroyOne)
                    )
                    manager.command(destroy
                        .literal("all")
                        .alexandriaHandler(::renderDestroyAll)
                    )
                }

            render.literal("edit")
                .alexandriaPermission("render.edit")
                .argument(IntegerArgument.of(ID)).let { edit ->
                    manager.command(edit
                        .literal("position")
                        .argument(LocationArgument.of(TO))
                        .alexandriaHandler(::renderEditPosition)
                    )
                    manager.command(edit
                        .literal("rotation")
                        .argument(EnumArgument.of(EulerOrder::class.java, ORDER))
                        .argumentFVec3(ANGLES)
                        .alexandriaHandler(::renderEditRotation)
                    )
                    manager.command(edit
                        .literal("scale")
                        .argumentFVec3(SCALE)
                        .alexandriaHandler(::renderEditScale)
                    )
                }
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

    private fun renderCreate(
        location: Location,
        descriptor: RenderDescriptor,
    ): Int {
        return ignacio.primitiveRenders.create(
            location.world,
            Transform(location.position()),
            descriptor,
        )
    }

    private fun renderCreateModel(ctx: Context) {
        val sender = ctx.sender
        val messages = ignacio.messages.forAudience(sender)
        val location = ctx.get<Location>(LOCATION)
        val item = ctx.get<ProtoItemStack>(ITEM).createItemStack(1, false)
        val scale = ctx.flag(SCALE) ?: 1.0f
        val billboard = ctx.flag(BILLBOARD) ?: Billboard.FIXED

        val renderId = renderCreate(location, ModelDescriptor(
            scale = FVec3(scale),
            billboard = billboard,
            item = item,
        ))

        messages.command.render.create.model(
            id = renderId,
            locationX = location.x, locationY = location.y, locationZ = location.z,
        ).sendTo(sender)
    }

    private fun renderCreateText(ctx: Context) {
        val sender = ctx.sender
        val messages = ignacio.messages.forAudience(sender)
        val location = ctx.get<Location>(LOCATION)
        val text = MiniMessage.miniMessage().deserialize(ctx.get(TEXT))
        val scale = ctx.flag(SCALE) ?: 1.0f
        val billboard = ctx.flag(BILLBOARD) ?: Billboard.CENTER

        val renderId = renderCreate(location, TextDescriptor(
            scale = FVec3(scale),
            billboard = billboard,
            text = text,
        ))

        messages.command.render.create.text(
            id = renderId,
            locationX = location.x, locationY = location.y, locationZ = location.z,
        ).sendTo(sender)
    }

    private fun renderDestroyOne(ctx: Context) {
        val sender = ctx.sender
        val messages = ignacio.messages.forAudience(sender)
        val id = ctx.get<Int>(ID)

        if (ignacio.primitiveRenders.destroy(id)) {
            messages.command.render.destroy.one(
                id = id,
            ).sendTo(sender)
        } else {
            messages.error.render.doesNotExist(
                id = id,
            ).sendTo(sender)
        }
    }

    private fun renderDestroyAll(ctx: Context) {
        val sender = ctx.sender
        val messages = ignacio.messages.forAudience(sender)

        val count = ignacio.primitiveRenders.count
        ignacio.primitiveRenders.destroyAll()

        messages.command.render.destroy.all(
            count = count,
        ).sendTo(sender)
    }

    private fun renderEditPosition(ctx: Context) {
        val sender = ctx.sender
        val messages = ignacio.messages.forAudience(sender)
        val id = ctx.get<Int>(ID)
        val to = ctx.get<Location>(TO)

        val render = ignacio.primitiveRenders[id] ?: run {
            messages.error.render.doesNotExist(
                id = id,
            ).sendTo(sender)
            return
        }

        render.transform = Transform(to.position(), render.transform.rotation)
    }

    private fun renderEditRotation(ctx: Context) {
        val sender = ctx.sender
        val messages = ignacio.messages.forAudience(sender)
        val id = ctx.get<Int>(ID)
        val rotation = asQuat(ctx.get<FVec3>(ANGLES), ctx.get(ORDER))

        val render = ignacio.primitiveRenders[id] ?: run {
            messages.error.render.doesNotExist(
                id = id,
            ).sendTo(sender)
            return
        }

        render.transform = Transform(render.transform.position, rotation)
    }

    private fun renderEditScale(ctx: Context) {
        val sender = ctx.sender
        val messages = ignacio.messages.forAudience(sender)
        val id = ctx.get<Int>(ID)
        val scale = ctx.get<FVec3>(SCALE)

        val render = ignacio.primitiveRenders[id] ?: run {
            messages.error.render.doesNotExist(
                id = id,
            ).sendTo(sender)
            return
        }

        render.scale = scale
    }

    private fun bodyCreate(
        location: Location,
        count: Int,
        spread: Double,
        virtual: Boolean,
        model: ItemDescriptor,
        scale: FVec3,
        createBody: (physics: PhysicsSpace, transform: Transform) -> PhysicsBody,
    ) {
        val item = model.create()
        repeat(count) {
            val transform = Transform(location.position() - spread + Random.nextDVec3() * (spread * 2))
            ignacio.primitiveBodies.create(
                location.world,
                transform,
                { physics -> createBody(physics, transform) },
                if (virtual) null else {
                    { tracker ->
                        ignacio.renders.create(ModelDescriptor(
                            item = item,
                            scale = scale,
                        ), tracker, transform)
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
        bodyCreate(location, count, spread, virtual, model, scale) { physics, transform ->
            physics.bodies.createStatic(descriptor, transform)
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
        bodyCreate(location, count, spread, virtual, model, scale) { physics, transform ->
            physics.bodies.createMoving(descriptor, transform)
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
