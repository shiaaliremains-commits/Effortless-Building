package my.easybuilding

import net.fabricmc.api.ModInitializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object Easybuilding : ModInitializer {
    const val MOD_ID = "easybuilding"
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        BuildManager.init()
        LOGGER.info("Easy Building loaded.")
    }
}
