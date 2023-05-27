package io.github.aecsocket.rattle

import cloud.commandframework.CommandManager
import cloud.commandframework.arguments.CommandArgument
import cloud.commandframework.arguments.standard.DoubleArgument
import cloud.commandframework.arguments.standard.IntegerArgument
import cloud.commandframework.context.CommandContext
import io.github.aecsocket.alexandria.hook.HookCommand
import io.github.aecsocket.glossa.messageProxy
import net.kyori.adventure.audience.Audience

private const val BOX = "box"
private const val CAPSULE = "capsule"
private const val CCD = "ccd"
private const val COUNT = "count"
private const val FRICTION = "friction"
private const val HALF_EXTENT = "half-extent"
private const val HALF_HEIGHT = "half-height"
private const val POSITION = "position"
private const val RADIUS = "radius"
private const val RESTITUTION = "restitution"
private const val SPHERE = "sphere"
private const val SPREAD = "spread"

typealias RealArgument<C> = DoubleArgument<C>

abstract class RattleCommand<C : Audience>(
    val rattle: RattleHook<*>,
    manager: CommandManager<C>,
) : HookCommand<C>(rattle, manager) {
    private val messages = rattle.glossa.messageProxy<RattleMessages>()

    init {
        root.literal("bodies").run {
            literal("create")
                .axPermission("bodies.create")
                .argument(positionArgumentOf(POSITION))
                .flag(manager.flagBuilder(COUNT)
                    .withAliases("n")
                    .withArgument(IntegerArgument.builder<C>(COUNT).withMin(1))
                )
                .flag(manager.flagBuilder(SPREAD)
                    .withAliases("s")
                    .withArgument(RealArgument.builder<C>(SPREAD).withMin(0))
                )
                .flag(manager.flagBuilder(FRICTION)
                    .withArgument(RealArgument.builder<C>(FRICTION).withMin(0))
                )
                .flag(manager.flagBuilder(RESTITUTION)
                    .withArgument(RealArgument.builder<C>(RESTITUTION).withMin(0))
                )
                .run {
                    literal("fixed")
                        .run {
                            manager.command(
                                literal(SPHERE)
                                    .argument(RealArgument.of(RADIUS))
                                    .axHandler(::bodiesCreateFixedSphere)
                            )
                            manager.command(
                                literal(BOX)
                                    .argument(RealArgument.of(HALF_EXTENT))
                                    .axHandler(::bodiesCreateFixedBox)
                            )
                            manager.command(
                                literal(CAPSULE)
                                    .argument(RealArgument.of(HALF_HEIGHT))
                                    .argument(RealArgument.of(RADIUS))
                                    .axHandler(::bodiesCreateFixedCapsule)
                            )
                        }

                    literal("moving")
                        .flag(manager.flagBuilder(CCD))
                        .run {
                            manager.command(
                                literal(SPHERE)
                                    .argument(RealArgument.of(RADIUS))
                                    .axHandler(::bodiesCreateMovingSphere)
                            )
                            manager.command(
                                literal(BOX)
                                    .argument(RealArgument.of(HALF_EXTENT))
                                    .axHandler(::bodiesCreateMovingBox)
                            )
                            manager.command(
                                literal(CAPSULE)
                                    .argument(RealArgument.of(HALF_HEIGHT))
                                    .argument(RealArgument.of(RADIUS))
                                    .axHandler(::bodiesCreateMovingCapsule)
                            )
                        }
                }

            literal("destroy")
                .axPermission("bodies.destroy")
                .run {
                    manager.command(literal("all")
                        .axHandler(::bodiesDestroyAll)
                    )
                }
        }
    }

    protected abstract fun positionArgumentOf(name: String): CommandArgument<C, *>

    private fun bodiesCreate(
        ctx: CommandContext<C>,

    ) {
        val sender = ctx.sender

    }

    private fun bodiesCreateFixed(
        ctx: CommandContext<C>,
        geom: Geometry,
    ) {
        // TODO
    }

    private fun bodiesCreateMoving(
        ctx: CommandContext<C>,
        geom: Geometry,
    ) {

    }

    private fun bodiesCreateFixedSphere(ctx: CommandContext<C>) {
        val radius = ctx.get<Real>(RADIUS)
        bodiesCreateFixed(ctx, Sphere(radius))
    }

    private fun bodiesCreateFixedBox(ctx: CommandContext<C>) {
        val halfExtent = ctx.get<Real>(HALF_EXTENT)
        bodiesCreateFixed(ctx, Box(Vec(halfExtent)))
    }

    private fun bodiesCreateFixedCapsule(ctx: CommandContext<C>) {
        val halfHeight = ctx.get<Real>(HALF_HEIGHT)
        val radius = ctx.get<Real>(RADIUS)
        bodiesCreateFixed(ctx, Capsule(halfHeight, radius))
    }

    private fun bodiesCreateMovingSphere(ctx: CommandContext<C>) {
        val radius = ctx.get<Real>(RADIUS)
        bodiesCreateMoving(ctx, Sphere(radius))
    }

    private fun bodiesCreateMovingBox(ctx: CommandContext<C>) {
        val halfExtent = ctx.get<Real>(HALF_EXTENT)
        bodiesCreateMoving(ctx, Box(Vec(halfExtent)))
    }

    private fun bodiesCreateMovingCapsule(ctx: CommandContext<C>) {
        val halfHeight = ctx.get<Real>(HALF_HEIGHT)
        val radius = ctx.get<Real>(RADIUS)
        bodiesCreateMoving(ctx, Capsule(halfHeight, radius))
    }

    private fun bodiesDestroyAll(ctx: CommandContext<C>) {
        // TODO
    }
}
