package io.github.aecsocket.ignacio.paper.render

import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.entity.type.EntityType
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.util.Quaternion4f
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity
import io.github.aecsocket.alexandria.paper.extension.nextEntityId
import io.github.aecsocket.ignacio.Transform
import io.github.aecsocket.ignacio.paper.sendPacket
import io.github.aecsocket.klam.FVec3
import io.github.aecsocket.klam.asARGB
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.entity.Display.Billboard
import org.bukkit.entity.Player
import java.util.*

sealed class DisplayRender(
    override var tracker: PlayerTracker,
    transform: Transform,
    val netId: Int,
    descriptor: RenderDescriptor,
) : Render {
    private fun metadataScale() =
        // 11: Display/Scale
        EntityData(11, EntityDataTypes.VECTOR3F, scale.run { Vector3f(x, y, z) })

    private fun metadataRotation() =
        // 12: Display/Rotation left
        EntityData(12, EntityDataTypes.QUATERNION, transform.rotation.run { Quaternion4f(x, y, z, w) })

    protected abstract val descriptor: RenderDescriptor

    private fun metadataBillboarding() =
        // 14: Display/Billboard constraints
        EntityData(14, EntityDataTypes.BYTE, when (descriptor.billboard) {
            Billboard.FIXED -> 0
            Billboard.VERTICAL -> 1
            Billboard.HORIZONTAL -> 2
            Billboard.CENTER -> 3
        }.toByte())

    override var transform = transform
        get() = Transform(field)
        set(value) {
            field = Transform(value)
            val packets = listOf(
                WrapperPlayServerEntityTeleport(
                    netId,
                    value.position.run { Vector3d(x, y, z) },
                    0f,
                    0f,
                    false,
                ),
                WrapperPlayServerEntityMetadata(netId, listOf(
                    metadataRotation(),
                ))
            )
            trackedPlayers().forEach { player ->
                packets.forEach { player.sendPacket(it) }
            }
        }

    override var scale = FVec3(descriptor.scale)
        get() = FVec3(field)
        set(value) {
            field = FVec3(value)
            val packet = WrapperPlayServerEntityMetadata(netId, listOf(
                metadataScale(),
            ))
            trackedPlayers().forEach { player ->
                player.sendPacket(packet)
            }
        }

    protected abstract fun entityType(): EntityType

    protected abstract fun metadata(): List<EntityData>

    override fun spawn(players: Iterable<Player>) {
        val packets = listOf(
            WrapperPlayServerSpawnEntity(
                netId,
                Optional.of(UUID.randomUUID()),
                entityType(),
                transform.position.run { Vector3d(x, y, z) },
                0f,
                0f,
                0f,
                0,
                Optional.empty(),
            ),
            WrapperPlayServerEntityMetadata(netId, listOf(
                metadataScale(),
                metadataRotation(),
                metadataBillboarding(),
            ) + metadata()),
        )
        players.forEach { player ->
            packets.forEach { player.sendPacket(it) }
        }
    }

    override fun despawn(players: Iterable<Player>) {
        val packet = WrapperPlayServerDestroyEntities(netId)
        players.forEach { player ->
            player.sendPacket(packet)
        }
    }
}

class DisplayModelRender(
    tracker: PlayerTracker,
    transform: Transform,
    netId: Int,
    override val descriptor: ModelDescriptor,
) : DisplayRender(tracker, transform, netId, descriptor), ModelRender {
    private fun metadataItem() =
        // 22: Item Display/Displayed item
        EntityData(22, EntityDataTypes.ITEMSTACK, SpigotConversionUtil.fromBukkitItemStack(item))

    override var item = descriptor.item
        set(value) {
            field = value
            val packet = WrapperPlayServerEntityMetadata(netId, listOf(
                metadataItem(),
            ))
            trackedPlayers().forEach { player ->
                player.sendPacket(packet)
            }
        }

    override fun entityType() = EntityTypes.ITEM_DISPLAY

    override fun metadata() = listOf(
        metadataItem(),
    )
}

class DisplayTextRender(
    tracker: PlayerTracker,
    transform: Transform,
    netId: Int,
    override val descriptor: TextDescriptor,
) : DisplayRender(tracker, transform, netId, descriptor), TextRender {
    private fun metadataText() =
        // 22: Text Display/Text
        EntityData(22, EntityDataTypes.COMPONENT, GsonComponentSerializer.gson().serialize(text))

    override var text = descriptor.text
        set(value) {
            field = value
            val packet = WrapperPlayServerEntityMetadata(netId, listOf(
                metadataText(),
            ))
            trackedPlayers().forEach { player ->
                player.sendPacket(packet)
            }
        }

    override fun entityType() = EntityTypes.TEXT_DISPLAY

    override fun metadata() = listOf(
        metadataText(),
        // 23: Text Display/Line width
        EntityData(23, EntityDataTypes.INT, descriptor.lineWidth),
        // 24: Text Display/Background color
        EntityData(24, EntityDataTypes.INT, asARGB(descriptor.backgroundColor)),
        // 26: Text Display/Bitfield
        EntityData(26, EntityDataTypes.BYTE, (
                (if (descriptor.hasShadow) 0x1 else 0) or
                (if (descriptor.isSeeThrough) 0x2 else 0) or
                when (descriptor.alignment) {
                    TextAlignment.CENTER -> 0
                    TextAlignment.LEFT -> 0x8
                    TextAlignment.RIGHT -> 0x10
                }
            ).toByte()
        )
    )
}

class DisplayRenders : Renders {
    override fun createModel(descriptor: ModelDescriptor, tracker: PlayerTracker, transform: Transform): ModelRender {
        return DisplayModelRender(tracker, transform, nextEntityId(), descriptor)
    }

    override fun createText(descriptor: TextDescriptor, tracker: PlayerTracker, transform: Transform): TextRender {
        return DisplayTextRender(tracker, transform, nextEntityId(), descriptor)
    }
}
