package io.github.aecsocket.rattle.paper

import io.github.aecsocket.alexandria.ArenaKey
import io.github.aecsocket.alexandria.ItemRenderDesc
import io.github.aecsocket.alexandria.paper.DisplayRenders
import io.github.aecsocket.alexandria.paper.extension.location
import io.github.aecsocket.klam.FAffine3
import io.github.aecsocket.klam.FQuat
import io.github.aecsocket.rattle.*
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.inventory.ItemStack

class PaperSimpleBodies(
    world: World,
    physics: PhysicsSpace,
    private val rattle: PaperRattle,
) : AbstractSimpleBodies<World>(world, physics, rattle.platform) {
    private val renders = DisplayRenders

    override fun createVisual(
        position: Iso,
        desc: SimpleBodyDesc,
        instance: Instance,
        instanceKey: ArenaKey,
    ) {
        val location = position.location(world)
        rattle.scheduling.onChunk(location).launch {
            val render = renders.createItem(
                world = world,
                position = position.translation,
                transform = FAffine3(rotation = FQuat(position.rotation)),
                item = ItemStack(Material.STONE),
                desc = ItemRenderDesc(),
            )

            rattle.scheduling.onEntity(render.entity).runRepeating { task ->
                if (instance.destroyed.get() || !render.entity.isValid) {
                    task.cancel()
                    destroy(instanceKey)
                    render.remove()
                    return@runRepeating
                }
                instance.nextPosition?.let { pos ->
                    render.position = pos.translation
                    render.transform = FAffine3(rotation = FQuat(pos.rotation))
                    instance.nextPosition = null
                }
            }
        }
    }
}
