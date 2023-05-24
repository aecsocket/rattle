package io.github.aecsocket.rattle.fabric

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

const val MOD_ID = "rattle"

class Rattle : ModInitializer {
    val log = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        log.info("it works")
    }
}
