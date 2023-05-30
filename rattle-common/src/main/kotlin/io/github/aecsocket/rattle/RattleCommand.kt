package io.github.aecsocket.rattle

import cloud.commandframework.CommandManager
import cloud.commandframework.arguments.CommandArgument
import cloud.commandframework.arguments.standard.BooleanArgument
import cloud.commandframework.arguments.standard.DoubleArgument
import cloud.commandframework.arguments.standard.IntegerArgument
import cloud.commandframework.context.CommandContext
import io.github.aecsocket.alexandria.extension.flag
import io.github.aecsocket.alexandria.extension.hasFlag
import io.github.aecsocket.alexandria.hook.AlexandriaCommand
import io.github.aecsocket.glossa.MessageProxy
import io.github.aecsocket.klam.nextDVec3
import io.github.aecsocket.rattle.stats.formatTiming
import io.github.aecsocket.rattle.stats.timingStatsOf
import net.kyori.adventure.audience.Audience
import kotlin.random.Random

private const val ALL = "all"
private const val ANG_DAMP = "ang-damp"
private const val BODY = "body"
private const val BOX = "box"
private const val CAPSULE = "capsule"
private const val CCD = "ccd"
private const val COUNT = "count"
private const val CREATE = "create"
private const val DENSITY = "density"
private const val DESTROY = "destroy"
private const val ENABLED = "enabled"
private const val FIXED = "fixed"
private const val FRICTION = "friction"
private const val GRAVITY_SCALE = "gravity-scale"
private const val HALF_EXTENT = "half-extent"
private const val HALF_HEIGHT = "half-height"
private const val LIN_DAMP = "lin-damp"
private const val LOCATION = "location"
private const val MASS = "mass"
private const val MOVING = "moving"
private const val RADIUS = "radius"
private const val RESTITUTION = "restitution"
private const val SPACE = "space"
private const val SPHERE = "sphere"
private const val SPREAD = "spread"
private const val STATS = "stats"
private const val VIRTUAL = "virtual"
private const val WORLD = "world"

typealias RealArgument<C> = DoubleArgument<C>

abstract class RattleCommand<C : Audience, W>(
    private val rattle: RattleHook<W>,
    private val messages: MessageProxy<RattleMessages>,
    manager: CommandManager<C>,
) : AlexandriaCommand<C>(rattle, manager) {
    protected abstract fun locationArgumentOf(key: String): CommandArgument<C, *>

    protected abstract fun CommandContext<C>.getLocation(key: String): Location<W>

    protected abstract fun worldArgumentOf(key: String): CommandArgument<C, *>

    protected abstract fun CommandContext<C>.getWorld(key: String): W

    protected abstract val CommandContext<C>.server: RattleServer<W, C>

    init {
        root.run {
            literal(SPACE)
                .argument(worldArgumentOf(WORLD))
                .run {
                    manager.command(
                        literal(CREATE)
                            .axPermission("$SPACE.$CREATE")
                            .axHandler(::spaceCreate)
                    )
                    manager.command(
                        literal(DESTROY)
                            .axPermission("$SPACE.$DESTROY")
                            .axHandler(::spaceDestroy)
                    )
                }

            literal(BODY).run {
                literal(CREATE)
                    .axPermission("$BODY.$CREATE")
                    .argument(locationArgumentOf(LOCATION))
                    .flag(manager.flagBuilder(COUNT)
                        .withAliases("n")
                        .withArgument(IntegerArgument.builder<C>(COUNT).withMin(1))
                    )
                    .flag(manager.flagBuilder(SPREAD)
                        .withAliases("s")
                        .withArgument(RealArgument.builder<C>(SPREAD).withMin(0))
                    )
                    .flag(manager.flagBuilder(DENSITY)
                        .withAliases("d")
                        .withArgument(RealArgument.builder<C>(DENSITY).withMin(0))
                    )
                    .flag(manager.flagBuilder(MASS)
                        .withAliases("m")
                        .withArgument(RealArgument.builder<C>(MASS).withMin(0))
                    )
                    .flag(manager.flagBuilder(FRICTION)
                        .withArgument(RealArgument.builder<C>(FRICTION).withMin(0))
                    )
                    .flag(manager.flagBuilder(RESTITUTION)
                        .withArgument(RealArgument.builder<C>(RESTITUTION).withMin(0))
                    )
                    .flag(manager.flagBuilder(VIRTUAL)
                        .withAliases("v")
                    )
                    .run {
                        literal(FIXED)
                            .run {
                                manager.command(
                                    literal(SPHERE)
                                        .argument(RealArgument.of(RADIUS))
                                        .axHandler(::bodyCreateFixedSphere)
                                )
                                manager.command(
                                    literal(BOX)
                                        .argument(RealArgument.of(HALF_EXTENT))
                                        .axHandler(::bodyCreateFixedBox)
                                )
                                manager.command(
                                    literal(CAPSULE)
                                        .argument(RealArgument.of(HALF_HEIGHT))
                                        .argument(RealArgument.of(RADIUS))
                                        .axHandler(::bodyCreateFixedCapsule)
                                )
                            }

                        literal(MOVING)
                            .flag(manager.flagBuilder(CCD))
                            .flag(manager.flagBuilder(GRAVITY_SCALE)
                                .withAliases("g")
                                .withArgument(RealArgument.builder<C>(GRAVITY_SCALE).withMin(0))
                            )
                            .flag(manager.flagBuilder(LIN_DAMP)
                                .withArgument(RealArgument.builder<C>(LIN_DAMP).withMin(0))
                            )
                            .flag(manager.flagBuilder(ANG_DAMP)
                                .withArgument(RealArgument.builder<C>(ANG_DAMP).withMin(0))
                            )
                            .run {
                                manager.command(
                                    literal(SPHERE)
                                        .argument(RealArgument.of(RADIUS))
                                        .axHandler(::bodyCreateMovingSphere)
                                )
                                manager.command(
                                    literal(BOX)
                                        .argument(RealArgument.of(HALF_EXTENT))
                                        .axHandler(::bodyCreateMovingBox)
                                )
                                manager.command(
                                    literal(CAPSULE)
                                        .argument(RealArgument.of(HALF_HEIGHT))
                                        .argument(RealArgument.of(RADIUS))
                                        .axHandler(::bodyCreateMovingCapsule)
                                )
                            }
                    }

                literal(DESTROY)
                    .axPermission("$BODY.$DESTROY")
                    .run {
                        manager.command(literal(ALL)
                            .axHandler(::bodyDestroyAll)
                        )
                    }
            }

            literal(STATS)
                .axPermission(STATS)
                .run {
                    manager.command(this.axHandler(::stats))

                    manager.command(argument(BooleanArgument.of(ENABLED))
                        .axHandler(::statsEnable))
                }
        }
    }

    private fun spaceCreate(ctx: CommandContext<C>) {
        val server = ctx.server
        val sender = ctx.sender
        val messages = messages.forAudience(sender)
        val world = ctx.getWorld(WORLD)

        if (server.hasPhysics(world)) {
            error(messages.error.space.alreadyExists(
                world = server.key(world).asString()
            ))
        }

        server.physicsOrCreate(world)

        messages.command.space.create(
            world = server.key(world).asString(),
        ).sendTo(sender)
    }

    private fun spaceDestroy(ctx: CommandContext<C>) {
        val server = ctx.server
        val sender = ctx.sender
        val messages = messages.forAudience(sender)
        val world = ctx.getWorld(WORLD)

        // block the command/main thread until physics world has been fully destroyed
        // this will wait until stepping and updates are complete
        server.physicsOrNull(world)?.withLock { (physics) ->
            physics.destroy()
        } ?: error(messages.error.space.doesNotExist(
            world = server.key(world).asString()
        ))

        messages.command.space.destroy(
            world = server.key(world).asString(),
        ).sendTo(sender)
    }

    private data class BodyCreateInfo(
        val count: Int,
        val positionX: Double,
        val positionY: Double,
        val positionZ: Double,
    )

    private fun bodyCreate(
        ctx: CommandContext<C>,
        createDesc: (PhysicsMaterial, Mass, Visibility) -> PrimitiveBodyDesc,
    ): BodyCreateInfo {
        val server = ctx.server
        val location = ctx.getLocation(LOCATION)
        val count = ctx.flag(COUNT) ?: 1
        val spread = ctx.flag(SPREAD) ?: 0.0
        val mass = ctx.flag<Real>(DENSITY)?.let { Mass.Density(it) }
            ?: ctx.flag<Real>(MASS)?.let { Mass.Constant(it) }
            ?: Mass.Density(1.0)
        val friction = ctx.flag(FRICTION) ?: DEFAULT_FRICTION
        val restitution = ctx.flag(RESTITUTION) ?: DEFAULT_RESTITUTION
        val virtual = ctx.hasFlag(VIRTUAL)

        val material = rattle.engine.createMaterial(
            friction = friction,
            restitution = restitution,
        )
        val visibility = if (virtual) Visibility.INVISIBLE else Visibility.VISIBLE

        repeat(count) {
            val offset = (Random.nextDVec3() * 2.0 - 1.0) * spread
            val position = location.position + offset

            server.primitiveBodies.create(
                location = Location(location.world, position),
                desc = createDesc(material, mass, visibility),
            )
        }

        return BodyCreateInfo(
            count = count,
            positionX = location.position.x,
            positionY = location.position.y,
            positionZ = location.position.z,
        )
    }

    private fun bodyCreateFixed(
        ctx: CommandContext<C>,
        geom: Geometry,
    ): BodyCreateInfo {
        return bodyCreate(ctx) { material, mass, visibility ->
            PrimitiveBodyDesc.Fixed(
                geometry = geom,
                material = material,
                mass = mass,
                visibility = visibility,
            )
        }
    }

    private fun bodyCreateMoving(
        ctx: CommandContext<C>,
        geom: Geometry,
    ): BodyCreateInfo {
        val ccd = ctx.hasFlag(CCD)
        val gravityScale = ctx.flag(GRAVITY_SCALE) ?: 1.0
        val linDamp = ctx.flag(LIN_DAMP) ?: DEFAULT_LINEAR_DAMPING
        val angDamp = ctx.flag(ANG_DAMP) ?: DEFAULT_ANGULAR_DAMPING
        return bodyCreate(ctx) { material, mass, visibility ->
            PrimitiveBodyDesc.Moving(
                geometry = geom,
                material = material,
                mass = mass,
                visibility = visibility,
                isCcdEnabled = ccd,
                gravityScale = gravityScale,
                linearDamping = linDamp,
                angularDamping = angDamp,
            )
        }
    }

    private fun sphereGeom(ctx: CommandContext<C>): Geometry {
        return Sphere(ctx.get(RADIUS))
    }

    private fun boxGeom(ctx: CommandContext<C>): Geometry {
        return Box(Vec(ctx.get(HALF_EXTENT)))
    }

    private fun capsuleGeom(ctx: CommandContext<C>): Geometry {
        return Capsule(
            ctx.get(HALF_HEIGHT),
            ctx.get(RADIUS),
        )
    }

    private fun bodyCreateFixedSphere(ctx: CommandContext<C>) {
        val sender = ctx.sender
        val (count, positionX, positionY, positionZ) = bodyCreateFixed(ctx, sphereGeom(ctx))
        messages.forAudience(sender).command.body.create.fixed.sphere(
            count = count,
            positionX = positionX, positionY = positionY, positionZ = positionZ,
        ).sendTo(sender)
    }

    private fun bodyCreateFixedBox(ctx: CommandContext<C>) {
        val sender = ctx.sender
        val (count, positionX, positionY, positionZ) = bodyCreateFixed(ctx, boxGeom(ctx))
        messages.forAudience(sender).command.body.create.fixed.box(
            count = count,
            positionX = positionX, positionY = positionY, positionZ = positionZ,
        ).sendTo(sender)
    }

    private fun bodyCreateFixedCapsule(ctx: CommandContext<C>) {
        val sender = ctx.sender
        val (count, positionX, positionY, positionZ) = bodyCreateFixed(ctx, capsuleGeom(ctx))
        messages.forAudience(sender).command.body.create.fixed.capsule(
            count = count,
            positionX = positionX, positionY = positionY, positionZ = positionZ,
        ).sendTo(sender)
    }

    private fun bodyCreateMovingSphere(ctx: CommandContext<C>) {
        val sender = ctx.sender
        val (count, positionX, positionY, positionZ) = bodyCreateMoving(ctx, sphereGeom(ctx))
        messages.forAudience(sender).command.body.create.moving.sphere(
            count = count,
            positionX = positionX, positionY = positionY, positionZ = positionZ,
        ).sendTo(sender)
    }

    private fun bodyCreateMovingBox(ctx: CommandContext<C>) {
        val sender = ctx.sender
        val (count, positionX, positionY, positionZ) = bodyCreateMoving(ctx, boxGeom(ctx))
        messages.forAudience(sender).command.body.create.moving.box(
            count = count,
            positionX = positionX, positionY = positionY, positionZ = positionZ,
        ).sendTo(sender)
    }

    private fun bodyCreateMovingCapsule(ctx: CommandContext<C>) {
        val sender = ctx.sender
        val (count, positionX, positionY, positionZ) = bodyCreateMoving(ctx, capsuleGeom(ctx))
        messages.forAudience(sender).command.body.create.moving.capsule(
            count = count,
            positionX = positionX, positionY = positionY, positionZ = positionZ,
        ).sendTo(sender)
    }

    private fun bodyDestroyAll(ctx: CommandContext<C>) {
        val server = ctx.server
        val sender = ctx.sender
        val messages = messages.forAudience(sender)

        val count = server.primitiveBodies.count
        server.primitiveBodies.destroyAll()
        messages.command.body.destroy.all(
            count = count,
        ).sendTo(sender)
    }

    private fun stats(ctx: CommandContext<C>) {
        val server = ctx.server
        val sender = ctx.sender
        val messages = messages.forAudience(sender)

        messages.command.stats.timingsHeader().sendTo(sender)

        rattle.settings.stats.timingBuffers.forEach { buffer ->
            val (median, best5, worst5) = timingStatsOf(server.engineTimings.getLast((buffer * 1000).toLong()))
            messages.command.stats.timing(
                buffer = buffer,
                median = formatTiming(median, messages),
                best5 = formatTiming(best5, messages),
                worst5 = formatTiming(worst5, messages),
            ).sendTo(sender)
        }

        val worlds = server.worlds
            .mapNotNull { server.physicsOrNull(it) }

        messages.command.stats.spacesHeader(
            count = worlds.size,
        ).sendTo(sender)

        worlds.forEach { world ->
            world.withLock { (physics, world) ->
                messages.command.stats.space(
                    world = server.key(world).asString(),
                    numColliders = physics.colliders.count,
                    numBodies = physics.bodies.count,
                    numActiveBodies = physics.bodies.activeCount,
                ).sendTo(sender)
            }
        }
    }

    private fun statsEnable(ctx: CommandContext<C>) {
        val server = ctx.server
        val sender = server.playerData(ctx.sender) ?: mustBePlayer(ctx.sender)
        val enabled = ctx.get<Boolean>(ENABLED)

        sender.showStatsBar(enabled)
    }
}
