package io.gitlab.aecsocket.ignacio.paper

import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.protocol.player.Equipment
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.PacketWrapper
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.ScoreBoardTeamInfo
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import io.gitlab.aecsocket.ignacio.core.math.EulerOrder
import io.gitlab.aecsocket.ignacio.core.math.Transform
import io.gitlab.aecsocket.ignacio.core.math.degrees
import io.gitlab.aecsocket.ignacio.core.math.euler
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import java.util.*
import kotlin.collections.HashMap

private const val HEIGHT_INTERP = -1.4385 // Y change from model to location of armor stand
private const val HEIGHT_NON_INTERP = -1.8135 // Y change from model to location of armor stand on AEC
private const val HEIGHT_SMALL = 0.7125 // Y change from normal to small stand to make the model position match
private const val HEIGHT_TEXT = 1.05 // Y change from model origin to nameplate origin

@Suppress("DEPRECATION")
private fun nextEntityId() = Bukkit.getUnsafe().nextEntityId()

private val air = ItemStack(Material.AIR)

private typealias PacketsProvider = () -> Iterable<PacketWrapper<*>>

fun interface IgPlayerTracker {
    fun trackedPlayers(): Collection<Player>
}

interface IgMesh {
    enum class Mode { ITEM, TEXT }

    @ConfigSerializable
    data class Settings(
        val interpolate: Boolean = true,
        val small: Boolean = false
    )

    val id: UUID
    var playerTracker: IgPlayerTracker
    var mode: Mode
    var transform: Transform
    var item: ItemStack
    var glowingColor: NamedTextColor

    fun trackedPlayers() = playerTracker.trackedPlayers()

    fun spawn(players: Iterable<Player>)
    fun spawn(player: Player) = spawn(setOf(player))
    fun spawn() = spawn(trackedPlayers())

    fun despawn(players: Iterable<Player>)
    fun despawn(player: Player) = despawn(setOf(player))
    fun despawn()

    fun name(text: Component?, players: Iterable<Player>)
    fun name(text: Component?, player: Player) = name(text, setOf(player))
    fun name(text: Component?) = name(text, trackedPlayers())

    fun glowing(state: Boolean, players: Iterable<Player>)
    fun glowing(state: Boolean, player: Player) = glowing(state, setOf(player))
    fun glowing(state: Boolean) = glowing(state, trackedPlayers())
}

class Meshes internal constructor() {
    private val _meshes = HashMap<UUID, BaseMesh>()
    val meshes: Map<UUID, IgMesh> get() = _meshes

    operator fun get(id: UUID): IgMesh? = _meshes[id]

    private fun create(
        transform: Transform,
        playerTracker: IgPlayerTracker,
        settings: IgMesh.Settings,
        mode: IgMesh.Mode,
        item: ItemStack
    ): IgMesh {
        val id = UUID.randomUUID()
        val mesh = if (settings.interpolate)
            InterpolatingMesh(id, transform, playerTracker, mode, item, settings.small)
        else
            NonInterpolatingMesh(id, transform, playerTracker, mode, item, settings.small)
        _meshes[id] = mesh
        return mesh
    }

    fun createItem(
        transform: Transform,
        playerTracker: IgPlayerTracker,
        settings: IgMesh.Settings,
        item: ItemStack
    ) = create(transform, playerTracker, settings, IgMesh.Mode.ITEM, item)

    fun createText(
        transform: Transform,
        playerTracker: IgPlayerTracker,
        settings: IgMesh.Settings
    ) = create(transform, playerTracker, settings, IgMesh.Mode.TEXT, air)

    fun remove(mesh: IgMesh, send: Boolean = true) {
        _meshes.remove(mesh.id)
        if (send) {
            mesh.despawn()
        }
    }

    internal fun update() {
        _meshes.forEach { (_, mesh) ->
            mesh.lastTrackedPlayers = mesh.trackedPlayers()
        }
    }

    private sealed class BaseMesh(
        override val id: UUID,
        transform: Transform,
        override var playerTracker: IgPlayerTracker,
        mode: IgMesh.Mode,
        item: ItemStack,
        val small: Boolean,
        private val baseYOffset: Double
    ) : IgMesh {
        val protocolId = nextEntityId()
        var lastTrackedPlayers: Iterable<Player> = emptySet()

        override var transform = transform
            set(value) {
                field = value
                sendToTracked(packetsTransform())
            }

        override var mode = mode
            set(value) {
                field = value
                yOffset = computeYOffset()
                sendToTracked(packetsTransform())
            }

        override var item = item
            set(value) {
                field = value
                sendToTracked(packetsItem())
            }

        override var glowingColor: NamedTextColor = NamedTextColor.WHITE
            set(value) {
                field = value
                sendToTracked(packetsGlowingColor())
            }

        private var yOffset = computeYOffset()

        override fun name(text: Component?, players: Iterable<Player>) {
            players.forEach { player ->
                player.sendPacket(WrapperPlayServerEntityMetadata(protocolId, listOf(
                    EntityData(2, EntityDataTypes.OPTIONAL_COMPONENT,
                        text?.let {
                            Optional.of(GsonComponentSerializer.gson().serialize(it))
                        } ?: Optional.empty<Component>()),
                    EntityData(3, EntityDataTypes.BOOLEAN, text != null)
                )))
            }
        }

        override fun glowing(state: Boolean, players: Iterable<Player>) {
            val flags = (0x20 or if (state) 0x40 else 0).toByte() // invisible + glowing?

            players.forEach { player ->
                player.sendPacket(WrapperPlayServerEntityMetadata(protocolId, listOf(
                    EntityData(0, EntityDataTypes.BYTE, flags)
                )))
            }
        }

        override fun despawn() {
            despawn(lastTrackedPlayers)
        }

        private fun computeYOffset() =
            baseYOffset + when (mode) {
                IgMesh.Mode.TEXT -> HEIGHT_TEXT
                IgMesh.Mode.ITEM -> if (small) HEIGHT_SMALL else 0.0
            }

        protected fun sendToTracked(packets: PacketsProvider) {
            trackedPlayers().forEach { player ->
                packets().forEach { player.sendPacket(it) }
            }
        }

        protected abstract fun packetsTransform(): PacketsProvider

        protected fun computePosition(transform: Transform): Vector3d {
            val (x, y, z) = transform.position
            return Vector3d(x, y + yOffset, z)
        }

        private fun computeHeadRotation(transform: Transform): Vector3f {
            val (x, y, z) = transform.rotation.euler(EulerOrder.ZYX).degrees()
            return Vector3f(x.toFloat(), -y.toFloat(), -z.toFloat())
        }

        protected fun packetsTransform(positionEntityId: Int, rotationEntityId: Int): PacketsProvider {
            val position = computePosition(transform)
            val headRotation = computeHeadRotation(transform)
            return {
                listOf(
                    WrapperPlayServerEntityTeleport(positionEntityId, position, 0f, 0f, false),
                    WrapperPlayServerEntityMetadata(rotationEntityId, listOf(
                        EntityData(16, EntityDataTypes.ROTATION, headRotation)
                    ))
                )
            }
        }

        private fun packetsItem(): PacketsProvider {
            val converted = SpigotConversionUtil.fromBukkitItemStack(item)
            return {
                listOf(
                    WrapperPlayServerEntityEquipment(protocolId, listOf(Equipment(EquipmentSlot.HELMET, converted)))
                )
            }
        }

        private fun packetsGlowingColor(): PacketsProvider {
            val teamName = IgnacioColorTeams.colorToTeam(glowingColor)
            val entryId = id.toString()
            return {
                listOf(
                    WrapperPlayServerTeams(
                        teamName,
                        WrapperPlayServerTeams.TeamMode.ADD_ENTITIES,
                        null as ScoreBoardTeamInfo?,
                        entryId
                    )
                )
            }
        }

        protected fun packetsSpawnArmorStand(): PacketsProvider {
            val packetsItem = packetsItem()
            val packetsGlowingColor = packetsGlowingColor()
            return {
                val position = computePosition(transform)
                val headRotation = computeHeadRotation(transform)
                listOf(
                    WrapperPlayServerSpawnEntity(protocolId,
                        Optional.of(id), EntityTypes.ARMOR_STAND,
                        position, 0f, 0f, 0f, 0, Optional.empty()),
                    WrapperPlayServerEntityMetadata(protocolId, listOf(
                        EntityData(0, EntityDataTypes.BYTE, (0x20).toByte()),
                        EntityData(15, EntityDataTypes.BYTE, ((if (small) 0x01 else 0) or 0x10).toByte()),
                        EntityData(16, EntityDataTypes.ROTATION, headRotation)
                    ))
                ) + packetsItem() + packetsGlowingColor()
            }
        }
    }

    private class InterpolatingMesh(
        id: UUID,
        transform: Transform,
        playerTracker: IgPlayerTracker,
        mode: IgMesh.Mode,
        item: ItemStack,
        small: Boolean
    ) : BaseMesh(id, transform, playerTracker, mode, item, small, HEIGHT_INTERP) {
        override fun packetsTransform() = packetsTransform(protocolId, protocolId)

        override fun spawn(players: Iterable<Player>) {
            val packets = packetsSpawnArmorStand()
            players.forEach { player ->
                packets().forEach { player.sendPacket(it) }
            }
        }

        override fun despawn(players: Iterable<Player>) {
            val entityIds = intArrayOf(protocolId)
            players.forEach {  player ->
                player.sendPacket(WrapperPlayServerDestroyEntities(*entityIds))
            }
        }
    }

    private class NonInterpolatingMesh(
        id: UUID,
        transform: Transform,
        playerTracker: IgPlayerTracker,
        mode: IgMesh.Mode,
        item: ItemStack,
        small: Boolean,
    ) : BaseMesh(id, transform, playerTracker, mode, item, small, HEIGHT_NON_INTERP) {
        val vehicleId = nextEntityId()

        override fun packetsTransform() = packetsTransform(vehicleId, protocolId)

        override fun spawn(players: Iterable<Player>) {
            val position = computePosition(transform)
            val packetsSpawn = packetsSpawnArmorStand()
            val vehicleUuid = UUID.randomUUID()
            val passengers = intArrayOf(protocolId)

            players.forEach { player ->
                packetsSpawn().forEach { player.sendPacket(it) }
                player.sendPacket(WrapperPlayServerSpawnEntity(vehicleId,
                    Optional.of(vehicleUuid), EntityTypes.AREA_EFFECT_CLOUD,
                    position, 0f, 0f, 0f, 0, Optional.empty()))
                player.sendPacket(WrapperPlayServerEntityMetadata(vehicleId, listOf(
                    EntityData(8, EntityDataTypes.FLOAT, 0f) // AEC radius
                )))
                player.sendPacket(WrapperPlayServerSetPassengers(vehicleId, passengers))
            }
        }

        override fun despawn(players: Iterable<Player>) {
            val entityIds = intArrayOf(protocolId, vehicleId)

            players.forEach { player ->
                 player.sendPacket(WrapperPlayServerDestroyEntities(*entityIds))
            }
        }
    }
}
