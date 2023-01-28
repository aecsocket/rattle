package io.gitlab.aecsocket.ignacio.paper

import io.gitlab.aecsocket.ignacio.core.util.force
import io.gitlab.aecsocket.ignacio.core.math.Vec3
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.format.TextColor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.block.data.BlockData
import org.bukkit.craftbukkit.v1_19_R2.CraftParticle
import org.bukkit.entity.Player
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.serialize.SerializationException
import org.spongepowered.configurate.serialize.TypeSerializer
import java.lang.reflect.Type

data class IgParticleEffect(
    val particle: Particle,
    val count: Int = 0,
    val size: Vec3 = Vec3.Zero,
    val speed: Double = 0.0,
    val data: Any? = null
) {
    fun spawn(player: Player, position: Vec3) {
        player.spawnParticle(particle,
            position.x, position.y, position.z, count,
            size.x, size.y, size.z, speed, data)
    }
}

internal object ParticleSerializer : TypeSerializer<Particle> {
    override fun serialize(type: Type, obj: Particle?, node: ConfigurationNode) {}

    override fun deserialize(type: Type, node: ConfigurationNode): Particle {
        val key = node.get<Key>()
            ?: throw SerializationException(node, type, "Particle must be expressed as namespaced key")
        val resourceKey = ResourceKey.create(Registries.PARTICLE_TYPE, ResourceLocation(key.namespace(), key.value()))
        val nmsParticle = BuiltInRegistries.PARTICLE_TYPE[resourceKey]
            ?: throw SerializationException("Invalid Particle $key")
        return CraftParticle.toBukkit(nmsParticle)
    }
}

internal object ColorSerializer : TypeSerializer<Color> {
    override fun serialize(type: Type, obj: Color?, node: ConfigurationNode) {}

    override fun deserialize(type: Type, node: ConfigurationNode) = Color.fromRGB(node.force<TextColor>().value())
}

private const val COLOR = "color"
private const val SIZE = "size"

internal object DustOptionsSerializer : TypeSerializer<Particle.DustOptions> {
    override fun serialize(type: Type, obj: Particle.DustOptions?, node: ConfigurationNode) {}

    override fun deserialize(type: Type, node: ConfigurationNode) = Particle.DustOptions(
        node.node(COLOR).force(),
        node.node(SIZE).force()
    )
}

internal object BlockDataSerializer : TypeSerializer<BlockData> {
    override fun serialize(type: Type, obj: BlockData?, node: ConfigurationNode) {}

    override fun deserialize(type: Type, node: ConfigurationNode) = try {
        Bukkit.createBlockData(node.force<String>())
    } catch (ex: IllegalArgumentException) {
        throw SerializationException(node, type, "Invalid block data", ex)
    }
}

private const val PARTICLE = "particle"
private const val COUNT = "count"
private const val SPEED = "speed"
private const val DATA = "data"

internal object ParticleEffectSerializer : TypeSerializer<IgParticleEffect> {
    override fun serialize(type: Type, obj: IgParticleEffect?, node: ConfigurationNode) {}

    override fun deserialize(type: Type, node: ConfigurationNode): IgParticleEffect {
        if (node.isMap) {
            val particle = node.node(PARTICLE).force<Particle>()
            val data = when (val dataType = particle.dataType) {
                Void::class.java -> null
                else -> node.node(DATA).get(dataType)
            }
            return IgParticleEffect(
                particle,
                node.node(COUNT).get { 0 },
                node.node(SIZE).get { Vec3.Zero },
                node.node(SPEED).get { 0.0 },
                data
            )
        } else return IgParticleEffect(
            node.force(), 0, Vec3.Zero, 0.0, null
        )
    }
}
