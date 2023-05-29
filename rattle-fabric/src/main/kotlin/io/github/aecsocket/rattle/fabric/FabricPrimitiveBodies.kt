package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.alexandria.Render
import io.github.aecsocket.alexandria.fabric.extension.toDVec
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.fabric.mixin.DisplayAccess
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Display.ItemDisplay
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.joml.Quaternionf

class FabricPrimitiveBodies : PrimitiveBodies<ServerLevel> {
    data class Body(
        val world: ServerLevel,
        val entity: ItemDisplay,
        val body: RigidBody,
        val collider: Collider,
        val render: Render?,
    ) {
        var nextPosition: Iso = Iso(entity.position().toDVec())
    }

    private val instances = ArrayList<Body>()

    override val count: Int
        get() = instances.size

    override fun create(
        world: ServerLevel,
        geom: Geometry,
        body: RigidBody,
        collider: Collider,
        visibility: Visibility,
    ) {
        val (translation) = body.readBody { it.position }
        val entity = ItemDisplay(EntityType.ITEM_DISPLAY, world)
        entity.moveTo(translation.x, translation.y, translation.z, 0.0f, 0.0f)
        entity.getSlot(0).set(ItemStack(Items.STONE))
        world.addFreshEntity(entity)

        val render: Render? = when (visibility) {
            Visibility.VISIBLE -> {
                null
            }
            Visibility.INVISIBLE -> null
        }

        world.physicsOrCreate().withLock { (physics) ->
            body.addTo(physics)
            collider.addTo(physics)
            collider.write { coll ->
                coll.parent = body
            }
        }

        instances += Body(
            world = world,
            entity = entity,
            body = body,
            collider = collider,
            render = render,
        )
    }

    override fun destroyAll() {
        instances.forEach { instance ->
            instance.entity.remove(Entity.RemovalReason.DISCARDED)
            instance.world.physicsOrNull()?.withLock { (physics) ->
                instance.body.remove()
                instance.collider.remove()
            }
            // instance.render?.despawn() // todo
        }
        instances.clear()
    }

    override fun onTick() {
        instances.toList().forEach { instance ->
            val (translation, rotation) = instance.nextPosition
            instance.entity.moveTo(translation.x, translation.y, translation.z)
            instance.entity.entityData.set(
                DisplayAccess.getDataLeftRotationId(),
                rotation.run { Quaternionf(x, y, z, w) },
            )
        }
    }

    override fun onPhysicsStep() {
        instances.forEach { instance ->
            instance.nextPosition = instance.body.readBody { it.position }
        }
    }
}
