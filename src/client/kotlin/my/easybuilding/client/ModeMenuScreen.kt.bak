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

class ModeMenuScreen : Screen(Component.literal("Easy Building")) {
    private lateinit var mirrorBtn: Button

    private fun label(mode: BuildMode): Component {
        return if (BuildState.mode == mode) {
            Component.literal("\u2714 " + mode.label).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
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

    private fun say(text: String) {
        val msg = Component.literal("[Easy Building] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
            .append(Component.literal(text).withStyle(ChatFormatting.WHITE))
        Minecraft.getInstance().player?.sendSystemMessage(msg)
    }

    private fun choose(mode: BuildMode) {
        BuildState.mode = mode
        say("Mode: ${mode.label}")
        onClose()
    }

    private fun setCenter(announce: Boolean) {
        val player = Minecraft.getInstance().player ?: return
        val pos = player.blockPosition()
        BuildState.mirrorCenter = pos
        if (announce) say("Mirror center set at (${pos.x}, ${pos.z})")
    }

    private fun cycleMirror() {
        val all = MirrorMode.entries
        BuildState.mirror = all[(BuildState.mirror.ordinal + 1) % all.size]
        if (BuildState.mirror != MirrorMode.OFF && BuildState.mirrorCenter == null) setCenter(true)
        mirrorBtn.message = mirrorLabel()
    }

    private fun addMode(mode: BuildMode, x: Int, y: Int, w: Int) {
        addRenderableWidget(Button.builder(label(mode)) { _ -> choose(mode) }.bounds(x, y, w, 20).build())
    }

    override fun init() {
        val cx = width / 2
        val cy = height / 2
        val bw = 84
        val rx = minOf(105, width / 2 - bw / 2 - 6)
        val ry = minOf(72, height / 2 - 50).coerceAtLeast(30)

        val header = Button.builder(Component.literal("Build Mode")) { _ -> }
            .bounds(cx - bw / 2, maxOf(4, cy - ry - 36), bw, 20).build()
        header.active = false
        addRenderableWidget(header)

        addMode(BuildMode.NORMAL, cx - bw / 2, cy - 10, bw)

        val outer = BuildMode.entries.filter { it != BuildMode.NORMAL }
        val step = 360.0 / outer.size
        for ((i, mode) in outer.withIndex()) {
            val a = Math.toRadians(-90.0 + step * i)
            val x = cx + (rx * cos(a)).toInt() - bw / 2
            val y = cy + (ry * sin(a)).toInt() - 10
            addMode(mode, x, y, bw)
        }

        // أزرار التحكم السفلية: المرآة + زر التراجع
        val mw = minOf(100, width / 3 - 6)
        mirrorBtn = Button.builder(mirrorLabel()) { _ -> cycleMirror() }
            .bounds(cx - mw * 3 / 2 - 4, height - 28, mw, 20).build()
        addRenderableWidget(mirrorBtn)

        addRenderableWidget(
            Button.builder(Component.literal("Set Mirror")) { _ -> setCenter(true) }
                .bounds(cx - mw / 2, height - 28, mw, 20).build()
        )

        // زر التراجع
        addRenderableWidget(
            Button.builder(Component.literal("↩ Undo (Ctrl+Z)")) { _ ->
                EasybuildingClient.sendUndo()
                onClose()
            }.bounds(cx + mw / 2 + 4, height - 28, mw, 20).build()
        )
    }
}
