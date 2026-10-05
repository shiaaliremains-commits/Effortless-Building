package my.easybuilding.client

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
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents

class ModeMenuScreen : Screen(Component.literal("Build Modes")) {

    private val rIn = 32.0f
    private val rOut = 84.0f
    private var hoveredIndex = -1

    override fun isPauseScreen(): Boolean = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)

        val cx = width / 2.0f
        val cy = height / 2.0f

        val dx = mouseX - cx
        val dy = mouseY - cy
        val dist = sqrt(dx * dx + dy * dy)

        val modes = BuildMode.entries
        val total = modes.size
        val anglePerSlice = (2 * Math.PI / total).toFloat()

        // حساب الشريحة التي يقف عليها مؤشر الماوس
        hoveredIndex = if (dist in 26.0f..rOut + 10.0f) {
            var angle = atan2(dy, dx)
            // محاذاة الشريحة الأولى في الأعلى تماماً (12 o'clock)
            var relAngle = angle - (-Math.PI.toFloat() / 2 - anglePerSlice / 2)
            while (relAngle < 0) relAngle += (2 * Math.PI).toFloat()
            while (relAngle >= 2 * Math.PI) relAngle -= (2 * Math.PI).toFloat()
            (relAngle / anglePerSlice).toInt().coerceIn(0, total - 1)
        } else {
            -1
        }

        // 1. رسم شرائح العجلة الدائرية
        for (i in 0 until total) {
            val mode = modes[i]
            val isCurrent = BuildState.mode == mode
            val isHovered = hoveredIndex == i

            // ألوان مطابقة للصورة تماماً
            val color = when {
                isHovered -> 0xF05CACDF.toInt() // أزرق سماوي مضيء (مثل الصورة)
                isCurrent -> 0xE60D7AC4.toInt() // أزرق ملوكي نشط
                else -> 0xDD3A3530.toInt()       // رمادي داكن شفاف
            }

            val centerAngle = -Math.PI.toFloat() / 2 + (i * anglePerSlice)
            val startAngle = centerAngle - (anglePerSlice / 2) + 0.035f
            val endAngle = centerAngle + (anglePerSlice / 2) - 0.035f

            val currentROut = if (isHovered) rOut + 3.0f else rOut
            renderSector(graphics, cx, cy, rIn, currentROut, startAngle, endAngle, color)

            // رسم الأيقونة المصغرة داخل كل شريحة
            val midR = (rIn + rOut) / 2.0f
            val iconX = (cx + cos(centerAngle) * midR).toInt()
            val iconY = (cy + sin(centerAngle) * midR).toInt()
            drawModeIcon(graphics, mode, iconX, iconY)
        }

        // 2. الفتحة الوسطية مع علامة +
        graphics.fill((cx - 16).toInt(), (cy - 16).toInt(), (cx + 16).toInt(), (cy + 16).toInt(), 0x88201C18.toInt())
        graphics.fill((cx - 4).toInt(), cy.toInt(), (cx + 5).toInt(), (cy + 1).toInt(), 0xBBFFFFFF.toInt())
        graphics.fill(cx.toInt(), (cy - 4).toInt(), (cx + 1).toInt(), (cy + 5).toInt(), 0xBBFFFFFF.toInt())

        // 3. كتابة اسم النمط على يمين العجلة عند التأشير (مثل كلمة Normal+ بالصورة)
        if (hoveredIndex in modes.indices) {
            val hoveredMode = modes[hoveredIndex]
            val textX = (cx + rOut + 16).toInt()
            val textY = (cy - 4).toInt()
            graphics.text(font, Component.literal(hoveredMode.label), textX, textY, 0xFFFFFFFF.toInt())
        }

        // 4. أزرار وأشرطة سفلية للـ Undo والـ Mirror
        drawBottomPills(graphics, cx.toInt(), height - 26)
    }

    /** رسم الشريحة الدائرية المفرغة بدمج سلس وبدون أخطاء */
    private fun renderSector(
        graphics: GuiGraphicsExtractor,
        cx: Float, cy: Float,
        rIn: Float, rOut: Float,
        startAngle: Float, endAngle: Float,
        color: Int
    ) {
        val rStep = 3.5f
        val rCount = ((rOut - rIn) / rStep).toInt()
        val totalAngle = endAngle - startAngle
        val aCount = (totalAngle / 0.045f).toInt().coerceAtLeast(1)
        val actualAngleStep = totalAngle / aCount

        for (a in 0..aCount) {
            val angle = startAngle + a * actualAngleStep
            val cosA = cos(angle)
            val sinA = sin(angle)
            for (r in 0..rCount) {
                val radius = rIn + r * rStep
                val px = cx + cosA * radius
                val py = cy + sinA * radius
                graphics.fill(px.toInt() - 1, py.toInt() - 1, px.toInt() + 3, py.toInt() + 3, color)
            }
        }
    }

    /** رسم مجسمات 3D مصغرة للأيقونات مثل صورة مود Effortless Building */
    private fun drawModeIcon(graphics: GuiGraphicsExtractor, mode: BuildMode, x: Int, y: Int) {
        when (mode.name) {
            "NORMAL" -> {
                drawIsoCube(graphics, x, y, 0xFFFFFFFF.toInt(), 0xFFCCCCCC.toInt(), 0xFF999999.toInt())
            }
            "LINE" -> {
                drawIsoCube(graphics, x - 5, y, 0xFFFFFFFF.toInt(), 0xFFCCCCCC.toInt(), 0xFF999999.toInt())
                drawIsoCube(graphics, x, y, 0xFFFFFFFF.toInt(), 0xFFCCCCCC.toInt(), 0xFF999999.toInt())
                drawIsoCube(graphics, x + 5, y, 0xFFFFFFFF.toInt(), 0xFFCCCCCC.toInt(), 0xFF999999.toInt())
            }
            "WALL" -> {
                drawIsoCube(graphics, x, y - 4, 0xFFFFFFFF.toInt(), 0xFFCCCCCC.toInt(), 0xFF999999.toInt())
                drawIsoCube(graphics, x, y, 0xFFFFFFFF.toInt(), 0xFFCCCCCC.toInt(), 0xFF999999.toInt())
                drawIsoCube(graphics, x, y + 4, 0xFFFFFFFF.toInt(), 0xFFCCCCCC.toInt(), 0xFF999999.toInt())
            }
            "FLOOR" -> {
                graphics.fill(x - 7, y - 2, x + 7, y + 2, 0xFFFFFFFF.toInt())
                graphics.fill(x - 7, y + 2, x + 7, y + 4, 0xFF888888.toInt())
            }
            "BOX" -> {
                graphics.fill(x - 6, y - 6, x + 6, y + 6, 0xFFFFFFFF.toInt())
                graphics.fill(x - 4, y - 4, x + 4, y + 4, 0xDD2A2520.toInt())
            }
            else -> {
                drawIsoCube(graphics, x, y, 0xFF88D8FF.toInt(), 0xFF44A8E0.toInt(), 0xFF2278B0.toInt())
            }
        }
    }

    private fun drawIsoCube(graphics: GuiGraphicsExtractor, x: Int, y: Int, top: Int, left: Int, right: Int) {
        graphics.fill(x - 3, y - 4, x + 4, y - 2, top)
        graphics.fill(x - 4, y - 2, x, y + 3, left)
        graphics.fill(x, y - 2, x + 4, y + 3, right)
    }

    private fun drawBottomPills(graphics: GuiGraphicsExtractor, cx: Int, y: Int) {
        val mirrorText = "[M] Mirror: ${BuildState.mirror.label}"
        val undoText = "↩ Undo (Ctrl+Z)"

        // شريط المرآة
        graphics.fill(cx - 130, y, cx - 10, y + 16, 0x99222220.toInt())
        graphics.centeredText(font, Component.literal(mirrorText), cx - 70, y + 4, 0xFFFFFFFF.toInt())

        // شريط التراجع
        graphics.fill(cx + 10, y, cx + 130, y + 16, 0x99222220.toInt())
        graphics.centeredText(font, Component.literal(undoText), cx + 70, y + 4, 0xFFFFFFFF.toInt())
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x()
        val my = event.y()
        val cx = width / 2.0
        val cy = height / 2.0

        // النقر على إحدى شرائح العجلة
        if (event.button() == 0 && hoveredIndex in BuildMode.entries.indices) {
            val selected = BuildMode.entries[hoveredIndex]
            BuildState.mode = selected

            Minecraft.getInstance().player?.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 1.2f)
            val msg = Component.literal("[Easy Building] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(Component.literal("Mode: ${selected.label}").withStyle(ChatFormatting.WHITE))
            Minecraft.getInstance().player?.sendSystemMessage(msg)

            onClose()
            return true
        }

        // النقر على زر التراجع السفلي
        if (event.button() == 0 && my in (height - 26).toDouble()..(height - 10).toDouble()) {
            if (mx in (cx + 10)..(cx + 130)) {
                EasybuildingClient.sendUndo()
                onClose()
                return true
            }
            if (mx in (cx - 130)..(cx - 10)) {
                cycleMirror()
                return true
            }
        }

        return super.mouseClicked(event, doubleClick)
    }

    private fun cycleMirror() {
        val all = MirrorMode.entries
        BuildState.mirror = all[(BuildState.mirror.ordinal + 1) % all.size]
        val player = Minecraft.getInstance().player
        if (BuildState.mirror != MirrorMode.OFF && BuildState.mirrorCenter == null && player != null) {
            BuildState.mirrorCenter = player.blockPosition()
        }
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val key = event.key()
        if (key == 256 || key == 66) { // زر ESC أو B للإغلاق
            onClose()
            return true
        }
        if (key == 77) { // زر M للتبديل السريع للمرآة
            cycleMirror()
            return true
        }
        return super.keyPressed(event)
    }
}
