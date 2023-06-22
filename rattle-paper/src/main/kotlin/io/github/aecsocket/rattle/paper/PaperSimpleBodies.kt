package io.github.aecsocket.rattle.paper

import io.github.aecsocket.alexandria.ArenaKey
import io.github.aecsocket.alexandria.desc.ItemDesc
import io.github.aecsocket.alexandria.paper.DisplayRenders
import io.github.aecsocket.alexandria.paper.ItemDisplayRender
import io.github.aecsocket.alexandria.paper.PaperItemType
import io.github.aecsocket.alexandria.paper.create
import io.github.aecsocket.alexandria.paper.extension.location
import io.github.aecsocket.alexandria.paper.extension.nextEntityId
import io.github.aecsocket.alexandria.paper.extension.sendPacket
import io.github.aecsocket.alexandria.paper.extension.spawn
import io.github.aecsocket.klam.FAffine3
import io.github.aecsocket.klam.FQuat
import io.github.aecsocket.klam.FVec3
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.world.SimpleBodies
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Marker

class PaperSimpleBodies(
    private val rattle: PaperRattle,
    world: World,
    physics: PhysicsSpace,
    settings: Settings = Settings(),
) : SimpleBodies<World>(world, rattle.platform, physics, settings) {
    override fun createVisual(
        position: Iso,
        geomSettings: Settings.Geometry?,
        geomScale: Vec,
        instance: Instance,
        instanceKey: ArenaKey,
    ) {
        val rGeomSettings = geomSettings
            ?: Settings.Geometry(ItemDesc(PaperItemType(Material.STONE)))
        val location = position.location(world)
        rattle.scheduling.onChunk(location).launch {
            val marker = world.spawn<Marker>(position.translation)
            val render = ItemDisplayRender(nextEntityId()) { packet ->
                // TODO
                Bukkit.getOnlinePlayers().forEach { it.sendPacket(packet) }
            }
                .spawn(position.translation)
                .transform(FAffine3(
                    rotation = position.rotation.toFloat(),
                    scale = geomScale.toFloat() * rGeomSettings.scale,
                ))
                .interpolation(settings.renderInterpolation)
            enders.createItem(
                world = world,
                position = position.translation,
                transform = FAffine3(
                ),
                item = rGeomSettings.item.create(),

                desc = settings.itemRenderDesc,
            )

            rattle.scheduling.onEntity(render.entity).runRepeating { task ->
                if (instance.destroyed.get() || !render.entity.isValid) {
                    task.cancel()
                    destroy(instanceKey)
                    render.remove()
                    return@runRepeating
                }
                instance.nextPosition?.let { pos ->
                    // this is required to make the interpolation work properly
                    // because this game SUCKS
                    //render.interpolationDelay = 0
                    render = pos.translation
                    render.transform = render.transform.copy(rotation = FQuat(pos.rotation))
                    instance.nextPosition = null
                }
            }
        }
    }
}
