package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.alexandria.ArenaKey
import io.github.aecsocket.alexandria.ItemRenderDesc
import io.github.aecsocket.alexandria.fabric.ItemRender
import io.github.aecsocket.klam.FAffine3
import io.github.aecsocket.klam.FQuat
import io.github.aecsocket.rattle.*
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class FabricSimpleBodies(
    world: ServerLevel,
    physics: PhysicsSpace,
    platform: FabricRattlePlatform,
) : AbstractSimpleBodies<ServerLevel>(world, physics, platform) {
    private inner class RenderInstance(
        val render: ItemRender,
        val instance: Instance,
        val instanceKey: ArenaKey,
    )

    private val renders = platform.renders
    private val renderInstances = ArrayList<RenderInstance>()

    override fun createVisual(
        position: Iso,
        desc: SimpleBodyDesc,
        instance: Instance,
        instanceKey: ArenaKey
    ) {
        val render = renders.createItem(
            world = world,
            position = position.translation,
            transform = FAffine3(rotation = FQuat(position.rotation)),
            item = ItemStack(Items.STONE),
            desc = ItemRenderDesc(),
        )
        renderInstances += RenderInstance(
            render = render,
            instance = instance,
            instanceKey = instanceKey,
        )
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

    override fun destroyAll() {
        super.destroyAll()
        renderInstances.forEach { ri ->
            ri.render.remove()
        }
        renderInstances.clear()
    }
}
