package io.github.aecsocket.ignacio.paper.display

import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.protocol.player.Equipment
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.ScoreBoardTeamInfo
import io.github.aecsocket.alexandria.paper.extension.nextEntityId
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import io.github.aecsocket.alexandria.core.math.EulerOrder
import io.github.aecsocket.alexandria.core.math.Transform
import io.github.aecsocket.alexandria.core.math.toEuler
import io.github.aecsocket.ignacio.paper.ColorTeams
import io.github.aecsocket.ignacio.paper.sendPacket
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*

private const val HEIGHT_INTERP = -1.4385 // Y change from model to location of armor stand
private const val HEIGHT_NON_INTERP = -1.8135 // Y change from model to location of armor stand on AEC
private const val HEIGHT_SMALL = 0.7125 // Y change from normal to small stand to make the model position match
private const val HEIGHT_TEXT = 1.05 // Y change from model origin to nameplate origin

sealed class StandRender(
    override var playerTracker: PlayerTracker,
    transform: Transform,
    private val yOffset: Double,
) : WorldRender {
    val protocolId = nextEntityId()
    val entityId: UUID = UUID.randomUUID()

    override var transform = transform
        set(value) {
            field = value
            val position = position(transform)
            val headRotation = headRotation(transform)
            trackedPlayers().forEach { player ->
                player.sendPacket(WrapperPlayServerEntityTeleport(protocolId,
                    position, 0f, 0f, false
                ))
                player.sendPacket(WrapperPlayServerEntityMetadata(protocolId, listOf(
                    EntityData(16, EntityDataTypes.ROTATION, headRotation)
                )))
            }
        }

    private fun position(transform: Transform) = transform.position
        .run { Vector3d(x, y + yOffset, z) }

    private fun headRotation(transform: Transform) = transform.rotation.toEuler(EulerOrder.ZYX).degrees()
        .run { Vector3f(x, -y, -z) }

    override fun spawn(players: Iterable<Player>) {
        val position = position(transform)
        val headRotation = headRotation(transform)
        players.forEach { player ->
            player.sendPacket(WrapperPlayServerSpawnEntity(
                protocolId, Optional.of(entityId), EntityTypes.ARMOR_STAND,
                position, 0f, 0f, 0f, 0, Optional.empty()
            ))
            player.sendPacket(WrapperPlayServerEntityMetadata(protocolId, listOf(
                EntityData(0, EntityDataTypes.BYTE, (0x20).toByte()),
                EntityData(15, EntityDataTypes.BYTE, (0x10).toByte()),
                EntityData(16, EntityDataTypes.ROTATION, headRotation),
            )))
        }
    }

    override fun despawn(players: Iterable<Player>) {
        players.forEach { player ->
            player.sendPacket(WrapperPlayServerDestroyEntities(protocolId))
        }
    }
}

class StandModel(
    playerTracker: PlayerTracker,
    transform: Transform,
    model: ItemStack,
) : StandRender(playerTracker, transform, HEIGHT_INTERP), WorldModel {
    override var glowingColor: NamedTextColor = NamedTextColor.WHITE
        set(value) {
            field = value
            val teamName = ColorTeams.colorToTeam(glowingColor)
            val entryId = entityId.toString()
            trackedPlayers().forEach { player ->
                player.sendPacket(WrapperPlayServerTeams(
                    teamName,
                    WrapperPlayServerTeams.TeamMode.ADD_ENTITIES,
                    null as ScoreBoardTeamInfo?,
                    entryId,
                ))
            }
        }

    override var model = model
        set(value) {
            field = value
            val headStack = SpigotConversionUtil.fromBukkitItemStack(value)
            trackedPlayers().forEach { player ->
                player.sendPacket(WrapperPlayServerEntityEquipment(protocolId, listOf(
                    Equipment(EquipmentSlot.HELMET, headStack)
                )))
            }
        }

    override fun spawn(players: Iterable<Player>) {
        super.spawn(players)
        val headStack = SpigotConversionUtil.fromBukkitItemStack(model)
        players.forEach { player ->
            player.sendPacket(WrapperPlayServerEntityEquipment(protocolId, listOf(
                Equipment(EquipmentSlot.HELMET, headStack)
            )))
        }
    }
}

class StandText(
    playerTracker: PlayerTracker,
    transform: Transform,
) : StandRender(playerTracker, transform, HEIGHT_INTERP + HEIGHT_TEXT), WorldText {
    override fun text(text: Component, players: Iterable<Player>) {
        players.forEach { player ->
            player.sendPacket(WrapperPlayServerEntityMetadata(protocolId, listOf(
                EntityData(2, EntityDataTypes.OPTIONAL_COMPONENT,
                    Optional.of(GsonComponentSerializer.gson().serialize(text))
                ),
                EntityData(3, EntityDataTypes.BOOLEAN, true),
            )))
        }
    }
}

class StandRenders : WorldRenders {
    override fun createModel(playerTracker: PlayerTracker, transform: Transform, model: ItemStack): StandModel {
        return StandModel(playerTracker, transform, model)
    }

    override fun createText(playerTracker: PlayerTracker, transform: Transform): WorldText {
        return StandText(playerTracker, transform)
    }
}
