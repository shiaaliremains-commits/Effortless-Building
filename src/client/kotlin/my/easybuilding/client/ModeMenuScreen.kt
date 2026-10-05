package my.easybuilding.client

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import my.easybuilding.BuildMode
import my.easybuilding.BuildState
import my.easybuilding.MirrorMode
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class ModeMenuScreen : Screen(Component.literal("Easy Building")) {

    private class Run(val y: Int, val x0: Int, val x1: Int, val seg: Int)

    private val modes: List<BuildMode> = BuildMode.entries.toList()
    private val icons = HashMap<BuildMode, ItemStack>()
    private val runs = ArrayList<Run>()

    private lateinit var mirrorBtn: Button
    private lateinit var autoDirBtn: Button
    private var cx = 0
    private var cy = 0
    private var rIn = 0
    private var rOut = 0
    private var hovered = -1

    private val colNormal = 0xC0393527.toInt()
    private val colHover = 0xE0A4C4E4.toInt()
    private val colSelected = 0xE03F7FC4.toInt()
    private val colSelectedHover = 0xE07FA8D8.toInt()
    private val colGap = 0xFF6E6A52.toInt()
    private val colWhite = 0xFFFFFFFF.toInt()

    override fun isPauseScreen(): Boolean = false

    private fun mirrorLabel(): Component {
        val m = BuildState.mirror
        return if (m == MirrorMode.OFF) {
            Component.literal("Mirror: Off")
        } else {
            Component.literal("Mirror: ${m.label}").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
        }
    }

    private fun autoDirLabel(): Component {
        val on = EasybuildingClient.autoDirection
        return Component.literal("Auto Dir: ").append(
            if (on) Component.literal("ON").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
            else Component.literal("OFF").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
        )
    }

    private fun say(text: String) {
        val msg = Component.literal("[Easy Building] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
            .append(Component.literal(text).withStyle(ChatFormatting.WHITE))
        Minecraft.getInstance().player?.sendSystemMessage(msg)
    }

    private fun choose(mode: BuildMode) {
        BuildState.mode = mode
        EasybuildingClient.cancelSelection()
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

    private fun iconFor(mode: BuildMode): ItemStack = icons.getOrPut(mode) {
        val n = mode.name.uppercase()
        val item: Item = when {
            mode == BuildMode.NORMAL -> Items.STONE
            "LINE" in n -> Items.STICK
            "WALL" in n -> Items.STONE_BRICKS
            "FLOOR" in n || "PLANE" in n || "FLAT" in n -> Items.SMOOTH_STONE_SLAB
            "CUBE" in n || "BOX" in n || "HOLLOW" in n -> Items.GLASS
            "SPHERE" in n || "DOME" in n -> Items.SLIME_BLOCK
            "CIRCLE" in n || "CYL" in n || "RING" in n -> Items.BARREL
            "PYRAMID" in n || "STAIR" in n -> Items.SANDSTONE_STAIRS
            else -> listOf(Items.QUARTZ_BLOCK, Items.QUARTZ_SLAB, Items.QUARTZ_STAIRS, Items.PURPUR_BLOCK)[mode.ordinal % 4]
        }
        ItemStack(item)
    }

    private fun segmentAt(dx: Double, dy: Double, withGap: Boolean): Int {
        val r = sqrt(dx * dx + dy * dy)
        if (r < rIn || r > rOut) return -2
        val n = modes.size
        val step = 2 * PI / n
        var a = atan2(dx, -dy)
        if (a < 0) a += 2 * PI
        val pos = a / step
        val idx = pos.toInt().coerceIn(0, n - 1)
        if (withGap) {
            val frac = pos - idx
            val edge = minOf(frac, 1 - frac) * step * r
            if (edge < 0.9) return -1
        }
        return idx
    }

    private fun hoverIndex(mx: Double, my: Double): Int {
        val s = segmentAt(mx - cx, my - cy, false)
        return if (s >= 0) s else -1
    }

    private fun buildRuns() {
        runs.clear()
        for (y in -rOut..rOut) {
            var x = -rOut
            while (x <= rOut) {
                val s = segmentAt(x + 0.5, y + 0.5, true)
                if (s == -2) { x++; continue }
                var e = x + 1
                while (e <= rOut && segmentAt(e + 0.5, y + 0.5, true) == s) e++
                runs.add(Run(y, x, e, s))
                x = e
            }
        }
    }

    override fun init() {
        cx = width / 2
        cy = height / 2 - 6
        rOut = minOf(92, height / 2 - 40, width / 3).coerceAtLeast(48)
        rIn = (rOut * 0.38).toInt()
        buildRuns()

        autoDirBtn = Button.builder(autoDirLabel()) { _ ->
            EasybuildingClient.autoDirection = !EasybuildingClient.autoDirection
            autoDirBtn.message = autoDirLabel()
            say("Auto Direction: ${if (EasybuildingClient.autoDirection) "ON" else "OFF"}")
        }.bounds(cx - 65, 8, 130, 20).build()
        addRenderableWidget(autoDirBtn)

        val mw = minOf(100, width / 3 - 6)
        mirrorBtn = Button.builder(mirrorLabel()) { _ -> cycleMirror() }
            .bounds(cx - mw * 3 / 2 - 4, height - 28, mw, 20).build()
        addRenderableWidget(mirrorBtn)

        addRenderableWidget(
            Button.builder(Component.literal("Set Mirror")) { _ -> setCenter(true) }
                .bounds(cx - mw / 2, height - 28, mw, 20).build()
        )

        addRenderableWidget(
            Button.builder(Component.literal("Undo (Ctrl+Z)")) { _ ->
                EasybuildingClient.sendUndo()
                onClose()
            }.bounds(cx + mw / 2 + 4, height - 28, mw, 20).build()
        )
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)

        hovered = hoverIndex(mouseX.toDouble(), mouseY.toDouble())
        val selected = modes.indexOf(BuildState.mode)

        for (run in runs) {
            val color = when {
                run.seg == -1 -> colGap
                run.seg == selected && run.seg == hovered -> colSelectedHover
                run.seg == selected -> colSelected
                run.seg == hovered -> colHover
                else -> colNormal
            }
            graphics.fill(cx + run.x0, cy + run.y, cx + run.x1, cy + run.y + 1, color)
        }

        graphics.fill(cx - 5, cy, cx + 6, cy + 1, colWhite)
        graphics.fill(cx, cy - 5, cx + 1, cy + 6, colWhite)

        val step = 2 * PI / modes.size
        val rm = (rIn + rOut) / 2.0
        for ((i, mode) in modes.withIndex()) {
            val a = -PI / 2 + step * (i + 0.5)
            val ix = (cx + rm * cos(a)).toInt() - 8
            val iy = (cy + rm * sin(a)).toInt() - 8
            graphics.item(iconFor(mode), ix, iy)
        }

        val shown = modes.getOrNull(hovered) ?: BuildState.mode
        val text = Component.literal(shown.label)
        val tx = cx + rOut + 12 + font.width(text) / 2
        graphics.centeredText(font, text, tx.coerceAtMost(width - font.width(text) / 2 - 4), cy - 4, colWhite)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (super.mouseClicked(event, doubleClick)) return true

        val dx = event.x() - cx
        val dy = event.y() - cy
        val idx = hoverIndex(event.x(), event.y())
        if (idx >= 0) {
            choose(modes[idx])
            return true
        }
        if (dx * dx + dy * dy < rIn * rIn) {
            onClose()
            return true
        }
        return false
    }
}
