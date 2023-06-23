package io.github.aecsocket.rattle.paper

import io.github.aecsocket.alexandria.ArenaKey
import io.github.aecsocket.alexandria.ItemRender
import io.github.aecsocket.alexandria.desc.ItemDesc
import io.github.aecsocket.alexandria.paper.*
import io.github.aecsocket.alexandria.paper.extension.location
import io.github.aecsocket.alexandria.paper.extension.nextEntityId
import io.github.aecsocket.alexandria.paper.extension.spawn
import io.github.aecsocket.klam.FAffine3
import io.github.aecsocket.klam.FQuat
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.world.SimpleBodies
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.Marker
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

class PaperSimpleBodies(
    private val rattle: PaperRattle,
    world: World,
    physics: PhysicsSpace,
    settings: Settings = Settings(),
) : SimpleBodies<World>(world, rattle.platform, physics, settings) {
    private val trackerToInstance = HashMap<UUID, ArenaKey>()

    data class PaperItem(val handle: ItemStack) : BakedItem

    override fun ItemRender.item(item: BakedItem) {
        (this as ItemDisplayRender).item((item as PaperItem).handle)
    }

    override fun ItemDesc.create() = PaperItem(create(count = 1))

    override fun defaultGeomSettings() = Settings.ForGeometry(
        item = ItemDesc(PaperItemType(Material.STONE)),
    )

    override fun createRender(position: Vec, instKey: ArenaKey): ItemRender {
        val tracker = world.spawn<Marker>(position) { tracker ->
            trackerToInstance[tracker.uniqueId] = instKey
        }

        rattle.scheduling.onEntity(tracker).runRepeating { task ->
            // UGHHHH REMOVAL CODE SUCKS
            // BAD BAD BAD
            fun removeTracker() {
                tracker.remove()
                trackerToInstance.remove(tracker.uniqueId)
            }

            if (!tracker.isValid) {
                task.cancel()
                removeTracker()
                return@runRepeating
            }

            val inst = get(instKey) ?: run {
                task.cancel()
                removeTracker()
                return@runRepeating
            }
            if (inst.destroyed.get()) {
                task.cancel()
            }


            if (instance.destroyed.get() || !tracker.isValid) {
                task.cancel()
                remove(instanceKey)
                render.remove()
                return@runRepeating
            }

            // logically it's gotta have a render. like come on, who would be stupid enough to run `createRender`
            // and then *remove* the render?
            // but just to be safe, we'll return instead of NPE'ing
            val render = inst.render ?: return@runRepeating
            render
                // this is required to make the interpolation work properly
                // because this game SUCKS
                .interpolationDelay(0)
                .position(inst.nextPosition.translation)
                .transform(FAffine3(
                    rotation = inst.nextPosition.rotation.toFloat(),
                    scale = inst.scale,
                ))
        }

        return ItemDisplayRender(nextEntityId(), tracker.playerReceivers())
    }

    fun onTrack(player: Player, entity: Entity) {
        val inst = trackerToInstance[entity.uniqueId]?.let { get(it) } ?: run {
            trackerToInstance.remove(entity.uniqueId)
            return
        }
        val render = inst.render as? ItemDisplayRender ?: return
        inst.onTrack(render.withReceiver(player.packetReceiver()))
    }

    fun onUntrack(player: Player, entity: Entity) {
        val inst = trackerToInstance[entity.uniqueId]?.let { get(it) } ?: run {
            trackerToInstance.remove(entity.uniqueId)
            return
        }
        val render = inst.render as? ItemDisplayRender ?: return
        inst.onUntrack(render.withReceiver(player.packetReceiver()))
    }

    fun onEntityRemove(entity: Entity) {
        val instKey = trackerToInstance.remove(entity.uniqueId) ?: return
        remove(instKey)
    }
}
