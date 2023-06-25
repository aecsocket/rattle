package io.github.aecsocket.rattle.paper

import io.github.aecsocket.alexandria.ArenaKey
import io.github.aecsocket.alexandria.ItemRender
import io.github.aecsocket.alexandria.extension.swapList
import io.github.aecsocket.alexandria.paper.*
import io.github.aecsocket.alexandria.paper.extension.nextEntityId
import io.github.aecsocket.alexandria.paper.extension.sendPacket
import io.github.aecsocket.alexandria.paper.extension.spawnTracker
import io.github.aecsocket.alexandria.sync.Locked
import io.github.aecsocket.klam.*
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.world.SimpleBodies
import org.bukkit.World
import org.bukkit.inventory.ItemStack
import kotlin.collections.HashSet

class PaperSimpleBodies(
    private val rattle: PaperRattle,
    world: World,
    physics: PhysicsSpace,
    settings: Settings = Settings(),
) : SimpleBodies<World>(world, rattle.platform, physics, settings) {
    private inner class PaperInstance(
        collider: ColliderKey,
        body: RigidBodyKey,
        scale: FVec3,
        position: DIso3,
        private val item: ItemStack,
    ) : SimpleBodies<World>.Instance(collider, body, scale, position) {
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
    ): Instance = PaperInstance(collider, body, scale, position, geomSettings.item.create())

    override fun createRender(position: DVec3, inst: Instance, instKey: ArenaKey): ItemRender {
        val render = ItemDisplayRender(nextEntityId()) {}
        rattle.scheduling.onChunk(world, position).runLater {
            val tracker = world.spawnTracker(position)
            val trackerId = tracker.uniqueId
            EntityTracking.register(tracker)
            EntityTracking.onTrack(tracker).invoke { player ->
                inst.onTrack(render.withReceiver(player.packetReceiver()))
            }
            EntityTracking.onUntrack(tracker).invoke { player ->
                inst.onUntrack(render.withReceiver(player.packetReceiver()))
            }
            render.receiver = PacketReceiver { packet ->
                EntityTracking.trackedPlayers(tracker).forEach { it.sendPacket(packet) }
            }

            rattle.scheduling.onEntity(tracker, onRetire = {
                // grab the last tracked players and clear them
                val receivers = EntityTracking.trackedPlayers(trackerId)
                render.receiver = PacketReceiver { packet ->
                    receivers.forEach { it.sendPacket(packet) }
                }
                EntityTracking.unregister(trackerId)
                // we have no clue on what thread this runnable will be run, so we can't delete it immediately
                toRemove.withLock { toRemove ->
                    toRemove += instKey
                }
            }).runRepeating {
                if (inst.destroyed.get()) {
                    tracker.remove()
                    return@runRepeating
                }
                inst.onUpdate()
            }
        }
        return render
    }

    override fun onPhysicsStep() {
        toRemove.withLock { it.swapList() }.forEach { remove(it) }
        super.onPhysicsStep()
    }
}
