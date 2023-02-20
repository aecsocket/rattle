package io.github.aecsocket.ignacio.paper

import cloud.commandframework.arguments.standard.FloatArgument
import cloud.commandframework.arguments.standard.IntegerArgument
import cloud.commandframework.bukkit.parsers.location.LocationArgument
import io.github.aecsocket.alexandria.api.paper.AlexandriaApiCommand
import io.github.aecsocket.alexandria.api.paper.Context
import io.github.aecsocket.alexandria.api.paper.extension.runDelayed
import io.github.aecsocket.alexandria.core.extension.flag
import io.github.aecsocket.glossa.core.messageProxy
import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.math.Transform
import io.github.aecsocket.ignacio.core.math.Vec3f
import io.github.aecsocket.ignacio.paper.display.item
import io.github.aecsocket.ignacio.paper.display.spawn
import io.github.aecsocket.ignacio.paper.util.position
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.AreaEffectCloud
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.inventory.ItemStack

private const val COUNT = "count"
private const val HALF_EXTENT = "half-extent"
private const val LOCATION = "location"
private const val MASS = "mass"
private const val RADIUS = "radius"

internal class IgnacioCommand(
    private val ignacio: Ignacio
) : AlexandriaApiCommand(ignacio, ignacio.glossa.messageProxy()) {
    init {
        root.literal("primitives").let { primitives ->
            primitives.literal("create")
                .argument(LocationArgument.of(LOCATION))
                .flag(manager.flagBuilder(COUNT)
                    .withAliases("n")
                    .withArgument(IntegerArgument.builder<CommandSender>(COUNT)
                        .withMin(1).build())
                )
                .flag(manager.flagBuilder(MASS)
                    .withAliases("m")
                    .withArgument(FloatArgument.builder<CommandSender>(MASS)
                        .withMin(0).build())
                )
                .let { create ->
                    manager.command(create
                        .literal("box")
                        .argument(FloatArgument.of(HALF_EXTENT))
                        .alexandriaPermission("primitives.create.box")
                        .handler(::primitivesCreateBox)
                    )
                    manager.command(create
                        .literal("sphere")
                        .argument(FloatArgument.of(RADIUS))
                        .alexandriaPermission("primitives.create.sphere")
                        .handler(::primitivesCreateSphere)
                    )
                }
            manager.command(primitives
                .literal("remove")
                .alexandriaPermission("primitives.remove")
                .handler(::primitivesRemove)
            )
        }
    }

    private fun primitivesCreate(
        location: Location,
        transform: Transform,
        physicsSpace: PhysicsSpace,
        body: PhysicsBody
    ) {
        val entity = location.world.spawnEntity(
            location, EntityType.ARMOR_STAND, CreatureSpawnEvent.SpawnReason.COMMAND
        ) { entity ->
            entity as ArmorStand
            entity.isVisible = false
            entity.isMarker = true
            entity.setCanTick(false)
        }
        val render = ignacio.renders.createItem(transform) { entity.trackedPlayers }
        render.spawn(transform)
        render.item(ItemStack(Material.STONE))
        ignacio.primitiveBodies.create(physicsSpace, body, entity, render)
    }

    private fun primitivesCreateBox(ctx: Context) {
        val sender = ctx.sender
        val messages = ignacio.messages.forAudience(sender)
        val location = ctx.get<Location>(LOCATION)
        val halfExtent = ctx.get<Float>(HALF_EXTENT)
        val count = ctx.flag(COUNT) ?: 1
        val mass = ctx.flag(MASS) ?: 1f

        val physicsSpace = ignacio.physicsSpaceOf(location.world)
        val transform = Transform(location.position())
        val body = physicsSpace.addDynamicBody(
            BoxGeometry(Vec3f(halfExtent)),
            transform,
            BodyDynamics(
                activate = true,
                mass = mass,
            )
        )
        primitivesCreate(location, transform, physicsSpace, body)

        messages.command.primitives.create.box(
            count = count,
            mass = mass,
            locationX = location.x, locationY = location.y, locationZ = location.z,
        ).sendTo(sender)
    }

    private fun primitivesCreateSphere(ctx: Context) {
        val sender = ctx.sender
        val messages = ignacio.messages.forAudience(sender)
        val location = ctx.get<Location>(LOCATION)
        val radius = ctx.get<Float>(RADIUS)
        val count = ctx.flag(COUNT) ?: 1
        val mass = ctx.flag(MASS) ?: 1f

        val physicsSpace = ignacio.physicsSpaceOf(location.world)
        val transform = Transform(location.position())
        val body = physicsSpace.addDynamicBody(
            SphereGeometry(radius),
            transform,
            BodyDynamics(
                activate = true,
                mass = mass,
            )
        )
        primitivesCreate(location, transform, physicsSpace, body)

        messages.command.primitives.create.sphere(
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
}
