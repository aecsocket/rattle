package io.github.aecsocket.rattle.paper

import io.github.aecsocket.alexandria.ArenaKey
import io.github.aecsocket.alexandria.ItemRender
import io.github.aecsocket.alexandria.desc.ItemDesc
import io.github.aecsocket.alexandria.paper.*
import io.github.aecsocket.alexandria.paper.extension.nextEntityId
import io.github.aecsocket.alexandria.paper.extension.sendPacket
import io.github.aecsocket.alexandria.paper.extension.spawn
import io.github.aecsocket.alexandria.sync.Locked
import io.github.aecsocket.klam.*
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.world.SimpleBodies
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
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
    private val toRemove = Locked(HashSet<ArenaKey>())

    data class PaperItem(val handle: ItemStack) : BakedItem

    override fun ItemRender.item(item: BakedItem) {
        (this as ItemDisplayRender).item((item as PaperItem).handle)
    }

    override fun ItemDesc.create() = PaperItem(create(count = 1))

    override fun createRender(position: DVec3, instKey: ArenaKey): ItemRender {
        // we start with no receivers, and update this set every tick
        // instead of using `entity.trackedPlayers` directly, because once that entity is invalid,
        // that returns an empty set; we want to get the last tracked players instead
        var lastReceivers: Set<Player> = emptySet()
        val render = ItemDisplayRender(nextEntityId()) { packet -> lastReceivers.forEach { it.sendPacket(packet) }}
        rattle.scheduling.onChunk(world, position).runLater {
            // since the client doesn't track Markers, we have to use something else
            val tracker = world.spawn<ItemDisplay>(position) { tracker ->
                trackerToInstance[tracker.uniqueId] = instKey
            }
            val trackerId = tracker.uniqueId

            rattle.scheduling.onEntity(tracker, onRetire = {
                // we have no clue on what thread this runnable will be run, so we can't delete it immediately
                trackerToInstance.remove(trackerId)?.let {
                    toRemove.withLock { toRemove ->
                        toRemove += it
                    }
                }
            }).runRepeating {
                val inst = get(instKey) ?: run {
                    tracker.remove()
                    return@runRepeating
                }
                if (inst.destroyed.get()) {
                    tracker.remove()
                    return@runRepeating
                }

                lastReceivers = tracker.trackedPlayers
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
        }
        return render
    }

    override fun onPhysicsStep() {
        // remove the previously-scheduled-for-removal instances
        // under protection of lock on both this SimpleBodies and the PhysicsSpace
        // copy and swap the set to avoid concurrency issues
        toRemove.withLock { toRemove -> toRemove.toSet().also { toRemove.clear() } }
            .forEach { remove(it) }
        super.onPhysicsStep()
    }

    fun onTrackEntity(player: Player, entity: Entity) {
        val inst = trackerToInstance[entity.uniqueId]?.let { get(it) } ?: run {
            trackerToInstance.remove(entity.uniqueId)
            return
        }
        val render = inst.render as? ItemDisplayRender ?: return
        inst.onTrack(render.withReceiver(player.packetReceiver()))
    }

    fun onUntrackEntity(player: Player, entity: Entity) {
        val inst = trackerToInstance[entity.uniqueId]?.let { get(it) } ?: run {
            trackerToInstance.remove(entity.uniqueId)
            return
        }
        val render = inst.render as? ItemDisplayRender ?: return
        inst.onUntrack(render.withReceiver(player.packetReceiver()))
    }
}
