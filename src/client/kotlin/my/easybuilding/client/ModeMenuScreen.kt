package my.easybuilding.client

import kotlin.math.cos
import kotlin.math.sin
import my.easybuilding.BuildMode
import my.easybuilding.BuildState
import my.easybuilding.MirrorMode
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class ModeMenuScreen : Screen(Component.literal("Build Modes")) {

    private lateinit var mirrorBtn: Button

    override fun isPauseScreen(): Boolean = false

    private fun choose(mode: BuildMode) {
        BuildState.mode = mode
        val msg = Component.literal("[Easy Building] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
            .append(Component.literal("Mode: ${mode.label}").withStyle(ChatFormatting.WHITE))
        Minecraft.getInstance().player?.sendSystemMessage(msg)
        onClose()
    }

    private fun modeLabel(mode: BuildMode): Component {
        return if (BuildState.mode == mode) {
            Component.literal("★ ${mode.label}").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
        } else {
            Component.literal(mode.label)
        }
    }

    private fun mirrorLabel(): Component {
        val m = BuildState.mirror
        return if (m == MirrorMode.OFF) {
            Component.literal("Mirror: Off")
        } else {
            Component.literal("Mirror: ${m.label}").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
        }
    }

    private fun cycleMirror() {
        val all = MirrorMode.entries
        BuildState.mirror = all[(BuildState.mirror.ordinal + 1) % all.size]
        val player = Minecraft.getInstance().player
        if (BuildState.mirror != MirrorMode.OFF && BuildState.mirrorCenter == null && player != null) {
            BuildState.mirrorCenter = player.blockPosition()
        }
        mirrorBtn.message = mirrorLabel()
    }

    override fun init() {
        val cx = width / 2
        val cy = height / 2

        // 1. مركز العجلة: نمط Normal في المنتصف تماماً مع علامة +
        val centerBtnWidth = 72
        val centerBtn = Button.builder(modeLabel(BuildMode.NORMAL)) { _ -> choose(BuildMode.NORMAL) }
            .bounds(cx - centerBtnWidth / 2, cy - 10, centerBtnWidth, 20)
            .build()
        addRenderableWidget(centerBtn)

        // 2. الشرائح الدائرية (Radial Ring): توزيع باقي الأنماط على شكل دائرة حول المركز
        val outerModes = BuildMode.entries.filter { it != BuildMode.NORMAL }
        val radiusX = minOf(110, width / 2 - 46)
        val radiusY = minOf(75, height / 2 - 45).coerceAtLeast(35)
        val btnW = 80
        val btnH = 20

        val step = (2 * Math.PI) / outerModes.size
        for ((i, mode) in outerModes.withIndex()) {
            val angle = -Math.PI / 2 + (step * i)
            val bx = (cx + radiusX * cos(angle)).toInt() - (btnW / 2)
            val by = (cy + radiusY * sin(angle)).toInt() - (btnH / 2)

            val btn = Button.builder(modeLabel(mode)) { _ -> choose(mode) }
                .bounds(bx, by, btnW, btnH)
                .build()
            addRenderableWidget(btn)
        }

        // 3. أزرار التحكم السفلية (Mirror و Undo)
        val bottomWidth = 110
        mirrorBtn = Button.builder(mirrorLabel()) { _ -> cycleMirror() }
            .bounds(cx - bottomWidth - 6, height - 28, bottomWidth, 20)
            .build()
        addRenderableWidget(mirrorBtn)

        val undoBtn = Button.builder(Component.literal("↩ Undo (Ctrl+Z)")) { _ ->
            EasybuildingClient.sendUndo()
            onClose()
        }.bounds(cx + 6, height - 28, bottomWidth, 20).build()
        addRenderableWidget(undoBtn)
    }
}
