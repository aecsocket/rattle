package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.alexandria.fabric.AlexandriaMod
import io.github.aecsocket.alexandria.hook.AlexandriaManifest
import io.github.aecsocket.rattle.RattleHook
import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object Rattle : AlexandriaMod<RattleHook.Settings>(AlexandriaManifest(
    id = "rattle",
    accentColor =
)) {
    val log = LoggerFactory.getLogger("Rattle")

    override fun onInitialize() {
        log.info("it works")
    }
}
