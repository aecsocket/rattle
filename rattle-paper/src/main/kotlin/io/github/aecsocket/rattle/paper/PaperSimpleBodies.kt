package io.github.aecsocket.rattle.paper

import io.github.aecsocket.alexandria.ItemRender
import io.github.aecsocket.alexandria.paper.*
import io.github.aecsocket.alexandria.paper.extension.*
import io.github.aecsocket.kbeam.ArenaKey
import io.github.aecsocket.kbeam.extension.swapList
import io.github.aecsocket.kbeam.sync.Locked
import io.github.aecsocket.klam.*
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.world.SimpleBodies
import io.github.aecsocket.rattle.world.Visibility
import kotlin.collections.HashSet
import org.bukkit.World
import org.bukkit.inventory.ItemStack

class PaperSimpleBodies(
    override val platform: PaperRattlePlatform,
    physics: PhysicsSpace,
    val world: World,
    settings: Settings = Settings(),
) : SimpleBodies(platform, physics, settings) {
  private inner class PaperInstance(
      collider: ColliderKey,
      body: RigidBodyKey,
      scale: FVec3,
      position: DIso3,
      private val item: ItemStack,
      override val render: ItemDisplayRender?,
  ) : SimpleBodies.Instance(collider, body, scale, position) {
    override fun ItemRender.item() {
      (this as ItemDisplayRender).item(item)
    }
  }

  private val toRemove = Locked(HashSet<ArenaKey>())

  override fun addInstance(
      collider: ColliderKey,
      body: RigidBodyKey,
      scale: FVec3,
      position: DIso3,
      geomSettings: Settings.ForGeometry,
      visibility: Visibility
  ): ArenaKey {
    return when (visibility) {
      Visibility.INVISIBLE ->
          instances.add(
              PaperInstance(
                  collider, body, scale, position, geomSettings.item.create(), render = null))
      Visibility.VISIBLE -> {
        val render = ItemDisplayRender(nextEntityId()) {}
        val inst =
            PaperInstance(collider, body, scale, position, geomSettings.item.create(), render)
        val instKey = instances.add(inst)

        platform.plugin.scheduling.onChunk(world, position.translation).runLater {
          val tracker =
              world.spawnTracker(position.translation) { tracker ->
                EntityTracking.register(tracker)
                EntityTracking.onTrack(tracker).run { player ->
                  inst.onTrack(render.withReceiver(player.packetReceiver()))
                }
                EntityTracking.onUntrack(tracker).run { player ->
                  inst.onUntrack(render.withReceiver(player.packetReceiver()))
                }
                render.receiver = PacketReceiver { packet ->
                  EntityTracking.trackedPlayers(tracker).forEach { it.sendPacket(packet) }
                }
              }
          val trackerId = tracker.uniqueId

          platform.plugin.scheduling
              .onEntity(
                  tracker,
                  onRetire = {
                    // grab the last tracked players and clear them
                    val receivers = EntityTracking.trackedPlayers(trackerId)
                    render.receiver = PacketReceiver { packet ->
                      receivers.forEach { it.sendPacket(packet) }
                    }
                    EntityTracking.unregister(trackerId)
                    // we have no clue on what thread this runnable will be run, so we can't delete
                    // it immediately
                    toRemove.withLock { it += instKey }
                  })
              .runRepeating {
                if (inst.destroyed.get()) {
                  tracker.remove()
                  return@runRepeating
                }
                val pos = inst.position.translation
                if (inst.onUpdate() && distanceSq(tracker.location.position(), pos) > 16.0 * 16.0) {
                  tracker.teleportAsync(pos.location(world))
                }
              }
        }
        instKey
      }
    }
  }

  override fun onPhysicsStep() {
    toRemove.withLock { it.swapList() }.forEach { remove(it) }
    super.onPhysicsStep()
  }
}
