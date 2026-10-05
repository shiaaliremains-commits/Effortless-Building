package my.easybuilding.client

import my.easybuilding.BuildMode
import my.easybuilding.BuildState
import my.easybuilding.MirrorMode
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class ModeMenuScreen : Screen(Component.literal("Easy Building Settings")) {
    override fun init() {
        super.init()
        val btnWidth = 180
        val btnHeight = 20
        val centerX = width / 2 - btnWidth / 2
        var y = height / 4

        // 1. زر تبديل وضع البناء
        addRenderableWidget(
            Button.builder(Component.literal("Mode: ${BuildState.mode.label}")) { btn ->
                val modes = BuildMode.entries
                val next = modes[(BuildState.mode.ordinal + 1) % modes.size]
                BuildState.mode = next
                btn.message = Component.literal("Mode: ${next.label}")
            }.bounds(centerX, y, btnWidth, btnHeight).build()
        )
        y += 24

        // 2. زر الميزة المطلوبة Auto Direction (ON / OFF)
        fun autoDirText() = "Auto Direction: " + if (BuildState.autoDirection) "ON" else "OFF"
        addRenderableWidget(
            Button.builder(Component.literal(autoDirText())) { btn ->
                BuildState.autoDirection = !BuildState.autoDirection
                btn.message = Component.literal(autoDirText())
            }.bounds(centerX, y, btnWidth, btnHeight).build()
        )
        y += 24

        // 3. زر المرآة (Mirror)
        addRenderableWidget(
            Button.builder(Component.literal("Mirror: ${BuildState.mirror.name}")) { btn ->
                val mirrors = MirrorMode.entries
                val next = mirrors[(BuildState.mirror.ordinal + 1) % mirrors.size]
                BuildState.mirror = next
                btn.message = Component.literal("Mirror: ${next.name}")
            }.bounds(centerX, y, btnWidth, btnHeight).build()
        )
        y += 24

        // 4. زر مسح مركز المرآة
        addRenderableWidget(
            Button.builder(Component.literal("Reset Mirror Center")) {
                BuildState.mirrorCenter = null
            }.bounds(centerX, y, btnWidth, btnHeight).build()
        )
        y += 28

        // زر إغلاق القائمة
        addRenderableWidget(
            Button.builder(Component.literal("Done")) {
                onClose()
            }.bounds(centerX, y, btnWidth, btnHeight).build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, height / 4 - 24, 0x55FFFF)
    }

    override fun isPauseScreen(): Boolean = false
}
