package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.alexandria.ArenaKey
import io.github.aecsocket.alexandria.ItemRender
import io.github.aecsocket.alexandria.extension.swapList
import io.github.aecsocket.alexandria.fabric.ItemDisplayRender
import io.github.aecsocket.alexandria.fabric.PacketReceiver
import io.github.aecsocket.alexandria.fabric.create
import io.github.aecsocket.alexandria.fabric.extension.createTrackerEntity
import io.github.aecsocket.alexandria.fabric.extension.nextEntityId
import io.github.aecsocket.alexandria.fabric.extension.toDVec
import io.github.aecsocket.alexandria.fabric.packetReceiver
import io.github.aecsocket.alexandria.sync.Locked
import io.github.aecsocket.klam.*
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.world.SimpleBodies
import io.github.aecsocket.rattle.world.Visibility
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Entity.RemovalReason
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
        override val render: ItemDisplayRender?
    ) : SimpleBodies<ServerLevel>.Instance(collider, body, scale, position) {
        override fun ItemRender.item() {
            (this as ItemDisplayRender).item(item)
        }
    }

    private val trackerToInst = Locked(HashMap<Entity, ArenaKey>())
    private val toRemove = Locked(HashSet<ArenaKey>())

    override fun addInstance(
        collider: ColliderKey,
        body: RigidBodyKey,
        scale: FVec3,
        position: DIso3,
        geomSettings: Settings.ForGeometry,
        visibility: Visibility,
    ): ArenaKey {
        return when (visibility) {
            Visibility.INVISIBLE -> instances.insert(
                FabricInstance(collider, body, scale, position, geomSettings.item.create(), render = null)
            )
            Visibility.VISIBLE -> {
                val tracker = createTrackerEntity(world, position.translation)
                val render = ItemDisplayRender(nextEntityId()) { packet ->
                    PlayerLookup.tracking(tracker).forEach { it.connection.send(packet) }
                }
                val instKey = instances.insert(
                    FabricInstance(collider, body, scale, position, geomSettings.item.create(), render)
                )
                trackerToInst.withLock { it[tracker] = instKey }
                world.addFreshEntity(tracker)
                instKey
            }
        }
    }

    override fun onPhysicsStep() {
        toRemove.withLock { it.swapList() }.forEach { remove(it) }
        super.onPhysicsStep()
    }

    fun onTick() {
        trackerToInst.withLock { trackerToInst ->
            trackerToInst.toList().forEach { (tracker, instKey) ->
                fun remove() {
                    tracker.remove(RemovalReason.DISCARDED)
                }

                val inst = get(instKey) ?: run {
                    remove()
                    return@forEach
                }
                if (inst.destroyed.get()) {
                    remove()
                    return@forEach
                }

                if (tracker.isRemoved) {
                    trackerToInst.remove(tracker)
                    toRemove.withLock { it += instKey }
                    return@forEach
                }

                val pos = inst.position.translation
                if (inst.onUpdate() && distanceSq(tracker.position().toDVec(), pos) > 16.0 * 16.0) {
                    tracker.teleportTo(pos.x, pos.y, pos.z)
                }
            }
        }
    }

    fun onTrackEntity(player: ServerPlayer, entity: Entity) {
        val inst = trackerToInst.withLock { it[entity] }?.let { get(it) } ?: return
        val render = inst.render as? ItemDisplayRender ?: return
        inst.onTrack(render.withReceiver(player.packetReceiver()))
    }

    fun onUntrackEntity(player: ServerPlayer, entity: Entity) {
        val inst = trackerToInst.withLock { it[entity] }?.let { get(it) } ?: return
        val render = inst.render as? ItemDisplayRender ?: return
        inst.onUntrack(render.withReceiver(player.packetReceiver()))
    }
}
