package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.alexandria.ArenaKey
import io.github.aecsocket.alexandria.desc.ItemDesc
import io.github.aecsocket.alexandria.fabric.FabricItemType
import io.github.aecsocket.alexandria.fabric.ItemRender
import io.github.aecsocket.alexandria.fabric.create
import io.github.aecsocket.klam.FAffine3
import io.github.aecsocket.klam.FQuat
import io.github.aecsocket.klam.FVec3
import io.github.aecsocket.rattle.*
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.Items

class FabricSimpleBodies(
    world: ServerLevel,
    platform: FabricRattlePlatform,
    physics: PhysicsSpace,
    settings: Settings = Settings(),
) : SimpleBodies<ServerLevel>(world, platform, physics, settings) {
    private inner class RenderInstance(
        val render: ItemRender,
        val instance: Instance,
        val instanceKey: ArenaKey,
    )

    private val renders = platform.renders
    private val renderInstances = ArrayList<RenderInstance>()

    override fun createVisual(
        position: Iso,
        geomSettings: Settings.Geometry?,
        geomScale: Vec,
        instance: Instance,
        instanceKey: ArenaKey,
    ) {
        val rGeomSettings = geomSettings
            ?: Settings.Geometry(ItemDesc(FabricItemType(Items.STONE)))
        val render = renders.createItem(
            world = world,
            position = position.translation,
            transform = FAffine3(
                rotation = FQuat(position.rotation),
                scale = FVec3(geomScale) * rGeomSettings.scale,
            ),
            item = rGeomSettings.item.create(),
            desc = settings.itemRenderDesc,
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
