package io.github.aecsocket.ignacio.paper

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.wrapper.PacketWrapper
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder
import io.github.aecsocket.ignacio.core.BoxGeometry
import io.github.aecsocket.ignacio.core.IgnacioEngine
import io.github.aecsocket.ignacio.core.PhysicsSpace
import io.github.aecsocket.ignacio.core.math.Transform
import io.github.aecsocket.ignacio.core.math.Vec3d
import io.github.aecsocket.ignacio.core.math.Vec3f
import io.github.aecsocket.ignacio.jolt.JoltEngine
import io.github.aecsocket.ignacio.paper.util.runRepeating
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.spongepowered.configurate.objectmapping.ConfigSerializable

private lateinit var instance: Ignacio
val IgnacioAPI get() = instance

class Ignacio : JavaPlugin() {
    companion object {
        @JvmStatic
        fun instance() = IgnacioAPI
    }

    @ConfigSerializable
    data class Settings(
        val a: Boolean = false
    )

    val engine: IgnacioEngine = JoltEngine()
    val meshes = StandMeshes()
    val physicsSpaces = HashMap<World, PhysicsSpace>()

    init {
        instance = this
    }

    override fun onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this))
        PacketEvents.getAPI().settings
            .checkForUpdates(false)
            .bStats(true)
        PacketEvents.getAPI().load()
    }

    override fun onEnable() {
        IgnacioCommand(this)

        runRepeating {
            meshes.update()
            physicsSpaces.forEach { (_, space) ->
                space.update(0.05f)
            }
        }
    }

    override fun onDisable() {
        physicsSpaces.forEach { (_, space) ->
            engine.destroySpace(space)
        }
        engine.destroy()
    }

    fun reload() {}

    fun physicsSpaceOf(world: World) = physicsSpaces.computeIfAbsent(world) {
        engine.createSpace(PhysicsSpace.Settings()).also {
            it.addStaticBody(
                BoxGeometry(Vec3f(10_000f, 0.5f, 10_000f)),
                Transform(Vec3d(0.0, 64.0, 0.0))
            )
        }
    }
}

internal fun Player.sendPacket(packet: PacketWrapper<*>) =
    PacketEvents.getAPI().playerManager.sendPacket(this, packet)
