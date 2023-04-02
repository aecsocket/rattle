package io.github.aecsocket.ignacio.paper

import cloud.commandframework.arguments.standard.DoubleArgument
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
import org.bukkit.inventory.ItemStack
import kotlin.random.Random

private const val COUNT = "count"
private const val DENSITY = "density"
private const val HALF_EXTENT = "half-extent"
private const val ID = "id"
private const val ITEM = "item"
private const val LOCATION = "location"
private const val MASS = "mass"
private const val RADIUS = "radius"
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
                    .handler(::spaceCreate)
                )
                manager.command(space
                    .literal("destroy")
                    .alexandriaPermission("space.destroy")
                    .handler(::spaceDestroy)
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
                        .handler(::renderCreateModel)
                    )
                    manager.command(create
                        .literal("text")
                        .argument(StringArgument.of(TEXT))
                        .handler(::renderCreateText)
                    )
                }

            render.literal("destroy")
                .alexandriaPermission("render.destroy").let { destroy ->
                    manager.command(destroy
                        .argument(IntegerArgument.of(ID))
                        .handler(::renderDestroyOne)
                    )
                    manager.command(destroy
                        .literal("all")
                        .handler(::renderDestroyAll)
                    )
                }

            render.literal("edit")
                .alexandriaPermission("render.edit")
                .argument(IntegerArgument.of(ID)).let { edit ->
                    manager.command(edit
                        .literal("move")
                        .argument(LocationArgument.of(TO))
                        .handler(::renderEditMove)
                    )
                }
        }

        root.literal("primitive").let { primitive ->
            primitive.literal("create")
                .alexandriaPermission("primitive.create")
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
                            .handler(::primitiveCreateStaticBox)
                        )
                        manager.command(static
                            .literal("sphere")
                            .argument(FloatArgument.of(RADIUS))
                            .handler(::primitiveCreateStaticSphere)
                        )
                    }

                    create.literal("moving")
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
                                .handler(::primitiveCreateMovingBox)
                            )
                            manager.command(moving
                                .literal("sphere")
                                .argument(FloatArgument.of(RADIUS))
                                .handler(::primitiveCreateMovingSphere)
                            )
                    }
                }

            manager.command(primitive
                .literal("destroy-all")
                .alexandriaPermission("primitives.destroy-all")
                .handler(::primitiveDestroyAll)
            )
        }
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

        val renderId = renderCreate(location, ModelDescriptor(
            scale = FVec3(scale),
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

        val renderId = renderCreate(location, TextDescriptor(
            scale = FVec3(scale),
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

        val count = ignacio.primitiveRenders.size
        ignacio.primitiveRenders.destroyAll()

        messages.command.render.destroy.all(
            count = count,
        ).sendTo(sender)
    }

    private fun renderEditMove(ctx: Context) {
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

    private fun primitiveCreate(
        location: Location,
        count: Int,
        spread: Double,
        virtual: Boolean,
        model: ItemDescriptor,
        addBody: (physics: PhysicsSpace, transform: Transform) -> PhysicsBody,
    ) {
        repeat(count) {
            val transform = Transform(location.position() - spread + Random.nextDVec3() * (spread * 2))
        }
    }

    private fun primitiveCreateStatic(
        location: Location,
        virtual: Boolean,
        model: ItemDescriptor,
        count: Int,
        spread: Double,
        geometry: Geometry,
    ) {
        val descriptor = StaticBodyDescriptor(
            shape = ignacio.engine.shape(geometry),
            contactFilter = ignacio.engine.contactFilter(ignacio.engine.layers.moving),
        )
        TODO()
//        primitiveCreate(location, count, spread, virtual, model) { physics, transform ->
//
//        }
    }

    private fun primitiveCreateStaticBox(ctx: Context) {
        val sender = ctx.sender
        val messages = ignacio.messages.forAudience(sender)
        val location = ctx.get<Location>(LOCATION)
        val halfExtent = ctx.get<Float>(HALF_EXTENT)
        val count = ctx.flag(COUNT) ?: 1
        val spread = ctx.flag(SPREAD) ?: 0.0
        val virtual = ctx.hasFlag(VIRTUAL)

//        primitiveCreateStatic(
//            location, count, spread, virtual,
//            ignacio.settings.primitiveModels.box,
//            BoxGeometry(Vec3(halfExtent)),
//        )

        messages.command.primitive.create.static.box(
            count = count,
            locationX = location.x, locationY = location.y, locationZ = location.z,
        ).sendTo(sender)
    }

    private fun primitiveCreateStaticSphere(ctx: Context) {

    }

    private fun primitiveCreateMovingBox(ctx: Context) {

    }

    private fun primitiveCreateMovingSphere(ctx: Context) {

    }

    private fun primitiveDestroyAll(ctx: Context) {
        val sender = ctx.sender
        val messages = ignacio.messages.forAudience(sender)

        val count = ignacio.primitiveBodies.size
        ignacio.primitiveBodies.destroyAll()

        messages.command.primitive.destroy.all(
            count = count,
        ).sendTo(sender)
    }
}
