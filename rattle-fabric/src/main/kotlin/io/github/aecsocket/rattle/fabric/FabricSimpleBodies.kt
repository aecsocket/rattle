package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.alexandria.ArenaKey
import io.github.aecsocket.alexandria.ItemRender
import io.github.aecsocket.alexandria.extension.swapList
import io.github.aecsocket.alexandria.fabric.ItemDisplayRender
import io.github.aecsocket.alexandria.fabric.create
import io.github.aecsocket.alexandria.fabric.extension.nextEntityId
import io.github.aecsocket.alexandria.fabric.extension.toVec3
import io.github.aecsocket.alexandria.sync.Locked
import io.github.aecsocket.klam.*
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.world.SimpleBodies
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Display.ItemDisplay
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.ItemStack

class FabricSimpleBodies(
    world: ServerLevel,
    platform: FabricRattlePlatform,
    physics: PhysicsSpace,
    settings: Settings = Settings(),
) : SimpleBodies<ServerLevel>(world, platform, physics, settings) {
    private inner class FabricInstance(
        collider: ColliderKey,
        body: RigidBodyKey,
        scale: FVec3,
        position: DIso3,
        private val item: ItemStack,
    ) : SimpleBodies<ServerLevel>.Instance(collider, body, scale, position) {
        override fun ItemRender.item() {
            (this as ItemDisplayRender).item(item)
        }
    }

    private val toRemove = Locked(HashSet<ArenaKey>())

    override fun createInstance(
        collider: ColliderKey,
        body: RigidBodyKey,
        scale: FVec3,
        position: DIso3,
        geomSettings: Settings.ForGeometry
    ): Instance = FabricInstance(collider, body, scale, position, geomSettings.item.create())

    override fun createRender(position: DVec3, inst: Instance, instKey: ArenaKey): ItemRender {
        val render = ItemDisplayRender(nextEntityId()) {}
        val tracker = ItemDisplay(EntityType.ITEM_DISPLAY, world)
        tracker.moveTo(position.toVec3())
    }

    fun onTick() {
        val iter = renderInstances.iterator()
        while (iter.hasNext()) {
            val ri = iter.next()
            val render = ri.render
            if (ri.instance.destroyed.get() || ri.render.entity.isRemoved) {
                destroy(ri.instanceKey)
                render.remove()
                iter.remove()
                continue
            }
            ri.instance.nextPosition?.let { pos ->
                render.position = pos.translation
                render.transform = FAffine3(rotation = FQuat(pos.rotation))
                ri.instance.nextPosition = null
            }
        }
    }

    override fun onPhysicsStep() {
        toRemove.withLock { it.swapList() }.forEach { remove(it) }
        super.onPhysicsStep()
    }
}
