package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.alexandria.fabric.AlexandriaMod
import io.github.aecsocket.alexandria.fabric.extension.alexandriaFabricSerializers
import io.github.aecsocket.alexandria.log.info
import io.github.aecsocket.rattle.RattleHook
import io.github.aecsocket.rattle.rattleManifest
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.ObjectMapper

object Rattle : AlexandriaMod<RattleHook.Settings>(rattleManifest) {
    override val configOptions: ConfigurationOptions = ConfigurationOptions.defaults()
        .serializers { it
            .registerAll(alexandriaFabricSerializers)
            .registerAnnotatedObjects(
                ObjectMapper.factoryBuilder()
                .addDiscoverer(dataClassFieldDiscoverer())
                .build()
            )
        }

    override fun onInitialize() {
        log.info { "it works" }
    }

    override fun loadSettings(node: ConfigurationNode) = node.get() ?: RattleHook.Settings()
}
