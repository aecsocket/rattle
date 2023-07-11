package io.github.aecsocket.rattle.impl

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
import io.github.aecsocket.klam.*
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.stats.formatTiming
import io.github.aecsocket.rattle.stats.timingStatsOf
import io.github.aecsocket.rattle.world.SimpleBodyDesc
import io.github.aecsocket.rattle.world.SimpleGeometry
import io.github.aecsocket.rattle.world.Visibility
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import net.kyori.adventure.audience.Audience

private const val ALL = "all"
private const val ANG_DAMP = "ang-damp"
private const val BODY = "body"
private const val BOX = "box"
private const val CCD = "ccd"
private const val COUNT = "count"
private const val CREATE = "create"
private const val DENSITY = "density"
private const val DESTROY = "destroy"
private const val DRAW = "draw"
private const val DYNAMIC = "dynamic"
private const val ENABLED = "enabled"
private const val FIXED = "fixed"
private const val FRICTION = "friction"
private const val GRAVITY_SCALE = "gravity-scale"
private const val HALF_EXTENT = "half-extent"
private const val LAUNCHER = "launcher"
private const val LIN_DAMP = "lin-damp"
private const val LOCATION = "location"
private const val MASS = "mass"
private const val NO_CCD = "no-ccd"
private const val RADIUS = "radius"
private const val RESTITUTION = "restitution"
private const val SPACE = "space"
private const val SPHERE = "sphere"
private const val SPREAD = "spread"
private const val STATS = "stats"
private const val TERRAIN = "terrain"
private const val VELOCITY = "velocity"
private const val VIRTUAL = "virtual"
private const val WORLD = "world"

abstract class RattleCommand<C : Audience>(
    private val rattle: RattleHook,
    private val messages: MessageProxy<RattleMessages>,
    manager: CommandManager<C>,
) : AlexandriaCommand<C>(rattle.ax, manager) {
  protected abstract fun locationArgumentOf(key: String): CommandArgument<C, *>

  protected abstract fun CommandContext<C>.getLocation(key: String): Location

  protected abstract fun worldArgumentOf(key: String): CommandArgument<C, *>

  protected abstract fun CommandContext<C>.getWorld(key: String): World

  protected abstract val CommandContext<C>.server: RattlePlatform

  protected abstract fun C.source(): CommandSource

  init {
    root.run {
      literal(SPACE).argument(worldArgumentOf(WORLD)).run {
        manager.command(literal(CREATE).axPermission("$SPACE.$CREATE").axHandler(::spaceCreate))
        manager.command(literal(DESTROY).axPermission("$SPACE.$DESTROY").axHandler(::spaceDestroy))
      }

      literal(BODY).run {
        literal(CREATE)
            .axPermission("$BODY.$CREATE")
            .argument(locationArgumentOf(LOCATION))
            .flag(
                manager
                    .flagBuilder(COUNT)
                    .withAliases("n")
                    .withArgument(IntegerArgument.builder<C>(COUNT).withMin(1)))
            .flag(
                manager
                    .flagBuilder(SPREAD)
                    .withAliases("s")
                    .withArgument(DoubleArgument.builder<C>(SPREAD).withMin(0)))
            .flag(
                manager
                    .flagBuilder(DENSITY)
                    .withAliases("d")
                    .withArgument(DoubleArgument.builder<C>(DENSITY).withMin(0)))
            .flag(
                manager
                    .flagBuilder(MASS)
                    .withAliases("m")
                    .withArgument(DoubleArgument.builder<C>(MASS).withMin(0)))
            .flag(
                manager
                    .flagBuilder(FRICTION)
                    .withArgument(DoubleArgument.builder<C>(FRICTION).withMin(0)))
            .flag(
                manager
                    .flagBuilder(RESTITUTION)
                    .withArgument(DoubleArgument.builder<C>(RESTITUTION).withMin(0)))
            .flag(manager.flagBuilder(VIRTUAL).withAliases("v"))
            .run {
              literal(FIXED).run {
                manager.command(
                    literal(SPHERE)
                        .argument(DoubleArgument.of(RADIUS))
                        .axHandler(::bodyCreateFixedSphere))
                manager.command(
                    literal(BOX)
                        .argument(DoubleArgument.of(HALF_EXTENT))
                        .axHandler(::bodyCreateFixedBox))
              }

              literal(DYNAMIC)
                  .flag(manager.flagBuilder(CCD))
                  .flag(
                      manager
                          .flagBuilder(GRAVITY_SCALE)
                          .withAliases("g")
                          .withArgument(DoubleArgument.builder<C>(GRAVITY_SCALE).withMin(0)))
                  .flag(
                      manager
                          .flagBuilder(LIN_DAMP)
                          .withArgument(DoubleArgument.builder<C>(LIN_DAMP).withMin(0)))
                  .flag(
                      manager
                          .flagBuilder(ANG_DAMP)
                          .withArgument(DoubleArgument.builder<C>(ANG_DAMP).withMin(0)))
                  .run {
                    manager.command(
                        literal(SPHERE)
                            .argument(DoubleArgument.of(RADIUS))
                            .axHandler(::bodyCreateDynamicSphere))
                    manager.command(
                        literal(BOX)
                            .argument(DoubleArgument.of(HALF_EXTENT))
                            .axHandler(::bodyCreateDynamicBox))
                  }
            }

        literal(DESTROY).argument(worldArgumentOf(WORLD)).axPermission("$BODY.$DESTROY").run {
          manager.command(literal(ALL).axHandler(::bodyDestroyAll))
        }
      }

      literal(STATS).axPermission(STATS).run {
        manager.command(this.axHandler(::stats))

        manager.command(argument(BooleanArgument.of(ENABLED)).axHandler(::statsEnable))
      }

      literal(LAUNCHER).axPermission(LAUNCHER).run {
        manager.command(axHandler(::launcherDisable))

        this.flag(
                manager
                    .flagBuilder(FRICTION)
                    .withArgument(DoubleArgument.builder<C>(FRICTION).withMin(0)))
            .flag(
                manager
                    .flagBuilder(RESTITUTION)
                    .withArgument(DoubleArgument.builder<C>(RESTITUTION).withMin(0)))
            .flag(
                manager
                    .flagBuilder(VELOCITY)
                    .withAliases("v")
                    .withArgument(DoubleArgument.builder<C>(VELOCITY).withMin(0)))
            .flag(
                manager
                    .flagBuilder(DENSITY)
                    .withAliases("d")
                    .withArgument(DoubleArgument.builder<C>(DENSITY).withMin(0)))
            .flag(manager.flagBuilder(NO_CCD))
            .run {
              manager.command(
                  literal(SPHERE).argument(DoubleArgument.of(RADIUS)).axHandler(::launcherSphere))
              manager.command(
                  literal(BOX).argument(DoubleArgument.of(HALF_EXTENT)).axHandler(::launcherBox))
            }
      }

      manager.command(
          literal(DRAW).axPermission(DRAW).flag(manager.flagBuilder(TERRAIN)).axHandler(::draw))
    }
  }

  private fun CommandContext<C>.runTask(task: () -> Unit) {
    val future = CompletableFuture<Unit>()
    rattle.runTask {
      task()
      future.complete(Unit)
    }

    try {
      future.get(
          (rattle.settings.jobs.commandTaskTerminateTime * 1000).toLong(), TimeUnit.MILLISECONDS)
    } catch (ex: Throwable) {
      rattle.log.warn { "Timed out waiting for task for command by $sender: $rawInputJoined" }
      error(messages.forAudience(sender).error.taskTimedOut())
    }
  }

  private fun spaceCreate(ctx: CommandContext<C>) {
    val server = ctx.server
    val sender = ctx.sender
    val messages = messages.forAudience(sender)
    val world = ctx.getWorld(WORLD)

    if (server.hasPhysics(world)) {
      error(messages.error.space.alreadyExists(world = server.key(world).asString()))
    }

    server.physicsOrCreate(world)

    messages.command.space
        .create(
            world = server.key(world).asString(),
        )
        .sendTo(sender)
  }

  private fun spaceDestroy(ctx: CommandContext<C>) {
    val server = ctx.server
    val sender = ctx.sender
    val messages = messages.forAudience(sender)
    val world = ctx.getWorld(WORLD)

    val lock =
        server.physicsOrNull(world)
            ?: error(messages.error.space.doesNotExist(world = server.key(world).asString()))
    ctx.runTask {
      lock.withLock { physics -> physics.destroy() }

      messages.command.space
          .destroy(
              world = server.key(world).asString(),
          )
          .sendTo(sender)
    }
  }

  private data class BodyCreateInfo(
      val count: Int,
      val positionX: Double,
      val positionY: Double,
      val positionZ: Double,
  )

  private fun bodyCreate(
      ctx: CommandContext<C>,
      createDesc: (PhysicsMaterial, Collider.Mass, Visibility) -> SimpleBodyDesc,
  ): BodyCreateInfo {
    val server = ctx.server
    val location = ctx.getLocation(LOCATION)
    val count = ctx.flag(COUNT) ?: 1
    val spread = ctx.flag(SPREAD) ?: 0.0
    val mass =
        ctx.flag<Double>(DENSITY)?.let { Collider.Mass.Density(it) }
            ?: ctx.flag<Double>(MASS)?.let { Collider.Mass.Constant(it) }
                ?: Collider.Mass.Density(1.0)
    val friction = ctx.flag(FRICTION) ?: DEFAULT_FRICTION
    val restitution = ctx.flag(RESTITUTION) ?: DEFAULT_RESTITUTION
    val virtual = ctx.hasFlag(VIRTUAL)

    val material =
        PhysicsMaterial(
            friction = friction,
            restitution = restitution,
        )
    val visibility = if (virtual) Visibility.INVISIBLE else Visibility.VISIBLE
    val desc = createDesc(material, mass, visibility)

    ctx.runTask {
      server.physicsOrCreate(location.world).withLock { physics ->
        repeat(count) {
          val offset = (Random.nextDVec3() * 2.0 - 1.0) * spread

          physics.simpleBodies.create(
              position = DIso3(location.position + offset, DQuat.identity),
              desc = desc,
          )
        }
      }
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
      geom: SimpleGeometry,
  ): BodyCreateInfo {
    return bodyCreate(ctx) { material, mass, visibility ->
      SimpleBodyDesc(
          type = RigidBodyType.FIXED,
          geom = geom,
          material = material,
          mass = mass,
          visibility = visibility,
      )
    }
  }

  private fun bodyCreateDynamic(
      ctx: CommandContext<C>,
      geom: SimpleGeometry,
  ): BodyCreateInfo {
    val ccd = ctx.hasFlag(CCD)
    val gravityScale = ctx.flag(GRAVITY_SCALE) ?: 1.0
    val linDamp = ctx.flag(LIN_DAMP) ?: DEFAULT_LINEAR_DAMPING
    val angDamp = ctx.flag(ANG_DAMP) ?: DEFAULT_ANGULAR_DAMPING
    return bodyCreate(ctx) { material, mass, visibility ->
      SimpleBodyDesc(
          type = RigidBodyType.DYNAMIC,
          geom = geom,
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

  private fun sphereGeom(ctx: CommandContext<C>): SimpleGeometry {
    return SimpleGeometry.Sphere(Sphere(ctx.get(RADIUS)))
  }

  private fun boxGeom(ctx: CommandContext<C>): SimpleGeometry {
    return SimpleGeometry.Box(Box(DVec3(ctx.get<Double>(HALF_EXTENT))))
  }

  private fun bodyCreateFixedSphere(ctx: CommandContext<C>) {
    val sender = ctx.sender
    val (count, positionX, positionY, positionZ) = bodyCreateFixed(ctx, sphereGeom(ctx))
    messages
        .forAudience(sender)
        .command
        .body
        .create
        .fixed
        .sphere(
            count = count,
            positionX = positionX,
            positionY = positionY,
            positionZ = positionZ,
        )
        .sendTo(sender)
  }

  private fun bodyCreateFixedBox(ctx: CommandContext<C>) {
    val sender = ctx.sender
    val (count, positionX, positionY, positionZ) = bodyCreateFixed(ctx, boxGeom(ctx))
    messages
        .forAudience(sender)
        .command
        .body
        .create
        .fixed
        .box(
            count = count,
            positionX = positionX,
            positionY = positionY,
            positionZ = positionZ,
        )
        .sendTo(sender)
  }

  private fun bodyCreateDynamicSphere(ctx: CommandContext<C>) {
    val sender = ctx.sender
    val (count, positionX, positionY, positionZ) = bodyCreateDynamic(ctx, sphereGeom(ctx))
    messages
        .forAudience(sender)
        .command
        .body
        .create
        .moving
        .sphere(
            count = count,
            positionX = positionX,
            positionY = positionY,
            positionZ = positionZ,
        )
        .sendTo(sender)
  }

  private fun bodyCreateDynamicBox(ctx: CommandContext<C>) {
    val sender = ctx.sender
    val (count, positionX, positionY, positionZ) = bodyCreateDynamic(ctx, boxGeom(ctx))
    messages
        .forAudience(sender)
        .command
        .body
        .create
        .moving
        .box(
            count = count,
            positionX = positionX,
            positionY = positionY,
            positionZ = positionZ,
        )
        .sendTo(sender)
  }

  private fun bodyDestroyAll(ctx: CommandContext<C>) {
    val server = ctx.server
    val sender = ctx.sender
    val messages = messages.forAudience(sender)
    val world = ctx.getWorld(WORLD)

    val lock =
        server.physicsOrNull(world)
            ?: error(messages.error.space.doesNotExist(world = server.key(world).asString()))

    ctx.runTask {
      lock.withLock { physics ->
        val count = physics.simpleBodies.count
        physics.simpleBodies.removeAll()
        messages.command.body.destroy
            .all(
                world = server.key(world).asString(),
                count = count,
            )
            .sendTo(sender)
      }
    }
  }

  private fun stats(ctx: CommandContext<C>) {
    val server = ctx.server
    val sender = ctx.sender
    val messages = messages.forAudience(sender)

    messages.command.stats.timingsHeader().sendTo(sender)

    rattle.settings.stats.timingBuffers.forEach { buffer ->
      val (median, best5, worst5) =
          timingStatsOf(server.engineTimings.getLast((buffer * 1000).toLong()))
      messages.command.stats
          .timing(
              buffer = buffer,
              median = formatTiming(median, messages),
              best5 = formatTiming(best5, messages),
              worst5 = formatTiming(worst5, messages),
          )
          .sendTo(sender)
    }

    val worlds =
        server.worlds.mapNotNull { world -> server.physicsOrNull(world)?.let { world to it } }

    messages.command.stats
        .spacesHeader(
            count = worlds.size,
        )
        .sendTo(sender)

    worlds.forEach { (world, physics) ->
      // if the physics engine is under heavy load, it might take us a while to fetch stats for a
      // space
      // during this time, we don't want to lock and block the main thread
      // scheduling a task will mean that the order the spaces are printed in is non-deterministic
      // but that's fine
      ctx.runTask {
        physics.withLock { (space) ->
          messages.command.stats
              .space(
                  world = server.key(world).asString(),
                  colliders = space.colliders.count,
                  rigidBodies = space.rigidBodies.count,
                  activeRigidBodies = space.rigidBodies.activeCount,
              )
              .sendTo(sender)
        }
      }
    }
  }

  private fun statsEnable(ctx: CommandContext<C>) {
    val server = ctx.server
    val sender = server.asPlayer(ctx.sender.source()) ?: mustBePlayer(ctx.sender)
    val enabled = ctx.get<Boolean>(ENABLED)

    sender.showStatsBar(enabled)
  }

  private fun launcherDisable(ctx: CommandContext<C>) {
    val server = ctx.server
    val sender = server.asPlayer(ctx.sender.source()) ?: mustBePlayer(ctx.sender)
    val messages = messages.forAudience(sender)

    if (sender.launcher != null) {
      sender.launcher = null
      messages.command.launcher.disable().sendTo(sender)
    }
  }

  private fun launcher(ctx: CommandContext<C>, sender: RattlePlayer, geom: SimpleGeometry) {
    val friction = ctx.flag(FRICTION) ?: DEFAULT_FRICTION
    val restitution = ctx.flag(RESTITUTION) ?: DEFAULT_RESTITUTION
    val velocity = ctx.flag(VELOCITY) ?: 10.0
    val density = ctx.flag(DENSITY) ?: 1.0
    val ccd = !ctx.hasFlag(NO_CCD)

    sender.launcher =
        RattlePlayer.Launcher(
            geom = geom,
            material =
                PhysicsMaterial(
                    friction = friction,
                    restitution = restitution,
                ),
            velocity = velocity,
            mass = Collider.Mass.Density(density),
            isCcdEnabled = ccd,
        )
  }

  private fun launcherSphere(ctx: CommandContext<C>) {
    val server = ctx.server
    val sender = server.asPlayer(ctx.sender.source()) ?: mustBePlayer(ctx.sender)
    val messages = messages.forAudience(sender)

    if (sender.launcher == null) {
      messages.command.launcher.sphere().sendTo(sender)
    }
    launcher(ctx, sender, sphereGeom(ctx))
  }

  private fun launcherBox(ctx: CommandContext<C>) {
    val server = ctx.server
    val sender = server.asPlayer(ctx.sender.source()) ?: mustBePlayer(ctx.sender)
    val messages = messages.forAudience(sender)

    if (sender.launcher == null) {
      messages.command.launcher.box().sendTo(sender)
    }
    launcher(ctx, sender, boxGeom(ctx))
  }

  private fun draw(ctx: CommandContext<C>) {
    val server = ctx.server
    val sender = server.asPlayer(ctx.sender.source()) ?: mustBePlayer(ctx.sender)

    val draw =
        RattlePlayer.Draw(
            terrain = ctx.hasFlag(TERRAIN),
        )
    server.setPlayerDraw(sender, if (draw.isEmpty()) null else draw)
  }
}
