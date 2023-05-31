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

class FabricPrimitiveBodies(
    private val rattle: RattleMod,
) : PrimitiveBodies<ServerLevel> {
    data class Instance(
        val world: ServerLevel,
        val entity: ItemDisplay,
        val shape: Shape,
        val collider: Collider,
        val body: RigidBody,
        val render: Render?,
    ) {
        var nextPosition: Iso = Iso(entity.position().toDVec())
    }

    private val instances = ArrayList<Instance>()

    override val count: Int
        get() = instances.size

    override fun create(location: Location<ServerLevel>, desc: PrimitiveBodyDesc) {
        val (world, translation) = location
        val shape = rattle.engine.createShape(desc.geom)
        val collider = rattle.engine.createCollider(
            shape = shape,
            material = desc.material,
            mass = desc.mass,
        )
        val position = Iso(translation)
        val body = when (desc) {
            is PrimitiveBodyDesc.Fixed -> rattle.engine.createFixedBody(
                position = position,
            )
            is PrimitiveBodyDesc.Moving -> rattle.engine.createMovingBody(
                position = position,
                isCcdEnabled = desc.isCcdEnabled,
                gravityScale = desc.gravityScale,
                linearDamping = desc.linearDamping,
                angularDamping = desc.angularDamping,
            )
        }

        val entity = ItemDisplay(EntityType.ITEM_DISPLAY, world)
        entity.moveTo(translation.x, translation.y, translation.z, 0.0f, 0.0f)
        entity.getSlot(0).set(ItemStack(Items.STONE))
        world.addFreshEntity(entity)

        val render: Render? = when (desc.visibility) {
            Visibility.VISIBLE -> {
                null // TODO
            }
            Visibility.INVISIBLE -> null
        }

        location.world.physicsOrCreate().withLock { (physics) ->
            physics.bodies.add(body)
            physics.colliders.add(collider)
            collider.write { coll ->
                coll.parent = body
            }
        }

        instances += Instance(
            world = location.world,
            entity = entity,
            shape = shape,
            collider = collider,
            body = body,
            render = render,
        )
    }

    override fun destroyAll() {
        instances.forEach { instance ->
            instance.entity.remove(Entity.RemovalReason.DISCARDED)
            instance.world.physicsOrNull()?.withLock { (physics) ->
                physics.colliders.remove(instance.collider)
                physics.bodies.remove(instance.body)
            }
            instance.collider.destroy()
            instance.body.destroy()
            instance.shape.destroy()
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
            instance.body.read { rb ->
                if (rb.isSleeping) return@read
                instance.nextPosition = rb.position
            }
        }
    }
}
