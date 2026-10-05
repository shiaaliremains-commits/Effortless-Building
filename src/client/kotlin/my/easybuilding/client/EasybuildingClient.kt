package my.easybuilding.client

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.resources.Identifier

object EasybuildingClient : ClientModInitializer {
    private const val MOD_ID = "easybuilding"
    private lateinit var menuKey: KeyMapping

    override fun onInitializeClient() {
        val category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"))
        menuKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping("key.easybuilding.menu", InputConstants.KEY_B, category)
        )
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (menuKey.consumeClick()) {
                if (client.player != null) openScreen(ModeMenuScreen())
            }
        }
    }

    /** the screen-opening method moved between versions (Minecraft.setScreen or gui.setScreen), so find it by name */
    fun openScreen(screen: Screen?) {
        val mc = Minecraft.getInstance()
        val gui = runCatching { mc.javaClass.getField("gui").get(mc) }.getOrNull()
        if (!invokeSetScreen(gui, screen)) invokeSetScreen(mc, screen)
    }

    private fun invokeSetScreen(target: Any?, screen: Screen?): Boolean {
        if (target == null) return false
        val method = target.javaClass.methods.firstOrNull {
            (it.name == "setScreen" || it.name == "setScreenAndShow") && it.parameterCount == 1
        } ?: return false
        method.invoke(target, screen)
        return true
    }
}
