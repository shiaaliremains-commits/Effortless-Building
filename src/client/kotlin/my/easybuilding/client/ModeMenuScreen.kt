package my.easybuilding.client

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import my.easybuilding.BuildMode
import my.easybuilding.BuildPayload
import my.easybuilding.BuildState
import my.easybuilding.MirrorMode
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.CoreShaders
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import org.joml.Matrix4f

class ModeMenuScreen : Screen(Component.literal("Build Modes")) {

    private val innerRadius = 36.0f
    private val outerRadius = 88.0f
    private var hoveredIndex = -1

    override fun isPauseScreen(): Boolean = false

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val cx = width / 2.0f
        val cy = height / 2.0f

        val dx = mouseX - cx
        val dy = mouseY - cy
        val dist = sqrt(dx * dx + dy * dy)

        val modes = BuildMode.entries
        val total = modes.size
        val anglePerSlice = (2 * Math.PI / total).toFloat()

        // حساب الشريحة التي يقف عليها الماوس
        hoveredIndex = if (dist in innerRadius..outerRadius + 14f) {
            var angle = atan2(dy, dx)
            // إزاحة زاوية البدء لتكون الشريحة الأولى في الأعلى تماماً
            angle += (Math.PI / 2).toFloat() + (anglePerSlice / 2)
            while (angle < 0) angle += (2 * Math.PI).toFloat()
            while (angle >= 2 * Math.PI) angle -= (2 * Math.PI).toFloat()
            (angle / anglePerSlice).toInt().coerceIn(0, total - 1)
        } else {
            -1
        }

        // 1. رسم شرائح العجلة
        renderRadialWheel(guiGraphics, cx, cy, modes)

        // 2. رسم علامة + في منتصف العجلة
        guiGraphics.drawCenteredString(font, "+", cx.toInt(), (cy - 4).toInt(), 0x77FFFFFF)

        // 3. رسم الأيقونات داخل كل شريحة
        renderIcons(guiGraphics, cx, cy, modes, anglePerSlice)

        // 4. كتابة اسم النمط بجانب العجلة عند التأشير (مثل الصورة: Normal+)
        if (hoveredIndex in modes.indices) {
            val hoveredMode = modes[hoveredIndex]
            val isCurrent = BuildState.mode == hoveredMode
            val text = if (isCurrent) "✔ ${hoveredMode.label}" else hoveredMode.label
            val color = if (isCurrent) 0x55FF55 else 0xFFFFFF

            // يظهر الاسم على يمين أو يسار العجلة
            val textX = (cx + outerRadius + 16).toInt()
            val textY = (cy - 4).toInt()
            guiGraphics.drawString(font, text, textX, textY, color, true)
        }

        // 5. اختصارات وتلميحات سريعة أسفل الشاشة
        val tip = "[B] Close | [Z / Ctrl+Z] Undo | [M] Mirror: ${BuildState.mirror.label}"
        guiGraphics.drawCenteredString(font, tip, cx.toInt(), height - 20, 0xAAAAAA)

        super.render(guiGraphics, mouseX, mouseY, delta)
    }

    private fun renderRadialWheel(guiGraphics: GuiGraphics, cx: Float, cy: Float, modes: List<BuildMode>) {
        val matrix = guiGraphics.pose().last().pose()
        val total = modes.size
        val anglePerSlice = (2 * Math.PI / total).toFloat()

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShader(CoreShaders.POSITION_COLOR)

        for (i in 0 until total) {
            val mode = modes[i]
            val isSelected = BuildState.mode == mode
            val isHovered = hoveredIndex == i

            // الألوان تماماً مثل الصورة:
            // أزرق داكن ناصع للمحدد، أزرق فاتح عند التأشير، ورمادي داكن شفاف للبقية
            val (r, g, b, a) = when {
                isHovered -> floatArrayOf(0.55f, 0.78f, 0.95f, 0.88f) // أزرق سماوي مضيء
                isSelected -> floatArrayOf(0.08f, 0.52f, 0.85f, 0.85f) // أزرق ملوكي نشط
                else -> floatArrayOf(0.22f, 0.22f, 0.20f, 0.75f)       // رمادي داكن هادئ
            }

            val startAngle = -Math.PI.toFloat() / 2 - (anglePerSlice / 2) + (i * anglePerSlice)
            val endAngle = startAngle + anglePerSlice

            drawSector(matrix, cx, cy, innerRadius, if (isHovered) outerRadius + 4f else outerRadius, startAngle, endAngle, r, g, b, a)

            // رسم خط فاصل ناعم بين الشرائح
            drawSeparator(matrix, cx, cy, innerRadius, outerRadius, startAngle)
        }

        RenderSystem.disableBlend()
    }

    private fun drawSector(
        matrix: Matrix4f, cx: Float, cy: Float,
        rIn: Float, rOut: Float,
        startAngle: Float, endAngle: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        val steps = 14
        val stepAngle = (endAngle - startAngle) / steps

        val tesselator = Tesselator.getInstance()
        val buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR)

        for (s in 0..steps) {
            val curAngle = startAngle + s * stepAngle
            val cosA = cos(curAngle)
            val sinA = sin(curAngle)

            // النقطة الخارجية
            buffer.addVertex(matrix, cx + cosA * rOut, cy + sinA * rOut, 0f).setColor(r, g, b, a)
            // النقطة الداخلية
            buffer.addVertex(matrix, cx + cosA * rIn, cy + sinA * rIn, 0f).setColor(r, g, b, a)
        }
    }

    private fun drawSeparator(matrix: Matrix4f, cx: Float, cy: Float, rIn: Float, rOut: Float, angle: Float) {
        val cosA = cos(angle)
        val sinA = sin(angle)
        val tesselator = Tesselator.getInstance()
        val buffer = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR)
        buffer.addVertex(matrix, cx + cosA * rIn, cy + sinA * rIn, 0f).setColor(0.12f, 0.12f, 0.12f, 0.9f)
        buffer.addVertex(matrix, cx + cosA * (rOut + 2f), cy + sinA * (rOut + 2f), 0f).setColor(0.12f, 0.12f, 0.12f, 0.9f)
    }

    private fun renderIcons(guiGraphics: GuiGraphics, cx: Float, cy: Float, modes: List<BuildMode>, anglePerSlice: Float) {
        val midRadius = (innerRadius + outerRadius) / 2.0f

        for (i in modes.indices) {
            val mode = modes[i]
            val centerAngle = -Math.PI.toFloat() / 2 + (i * anglePerSlice)
            val iconX = cx + cos(centerAngle) * midRadius
            val iconY = cy + sin(centerAngle) * midRadius

            drawModeSymbol(guiGraphics, mode, iconX.toInt(), iconY.toInt())
        }
    }

    /** رسم أيقونات هندسية مصغرة تعبر عن نمط البناء تماماً مثل الصورة */
    private fun drawModeSymbol(guiGraphics: GuiGraphics, mode: BuildMode, x: Int, y: Int) {
        val white = 0xFFFFFFFF.toInt()
        val border = 0xFF2A2A2A.toInt()

        when (mode.name) {
            "NORMAL" -> {
                // بلوكة مكعبة صغيرة مفردة
                guiGraphics.fill(x - 5, y - 5, x + 5, y + 5, border)
                guiGraphics.fill(x - 4, y - 4, x + 4, y + 4, white)
            }
            "NORMAL_PLUS", "NORMALPLUS" -> {
                // بلوكة زجاجية / مميزة مفرغة بالوسط
                guiGraphics.fill(x - 6, y - 6, x + 6, y + 6, 0xFF66CCFF.toInt())
                guiGraphics.fill(x - 4, y - 4, x + 4, y + 4, 0xFF204060.toInt())
                guiGraphics.fill(x - 2, y - 2, x + 2, y + 2, white)
            }
            "LINE" -> {
                // خط مستقيم من البلوكات
                guiGraphics.fill(x - 9, y - 3, x + 9, y + 3, border)
                guiGraphics.fill(x - 8, y - 2, x + 8, y + 2, white)
            }
            "WALL" -> {
                // جدار رأسي قائم
                guiGraphics.fill(x - 7, y - 7, x + 7, y + 7, border)
                guiGraphics.fill(x - 6, y - 6, x + 6, y + 6, white)
                guiGraphics.fill(x - 6, y - 1, x + 6, y + 1, border)
            }
            "FLOOR" -> {
                // أرضية مسطحة ممتدة
                guiGraphics.fill(x - 8, y - 3, x + 8, y + 4, border)
                guiGraphics.fill(x - 7, y - 2, x + 7, y + 3, white)
            }
            "BOX" -> {
                // صندوق مجوف
                guiGraphics.fill(x - 7, y - 7, x + 7, y + 7, white)
                guiGraphics.fill(x - 4, y - 4, x + 4, y + 4, 0xFF333333.toInt())
            }
            else -> {
                // الحرف الأول للأنماط الأخرى مثل Circle / Cylinder / Sphere
                val initial = mode.label.take(1)
                guiGraphics.drawCenteredString(font, initial, x, y - 4, white)
            }
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && hoveredIndex in BuildMode.entries.indices) {
            val selected = BuildMode.entries[hoveredIndex]
            BuildState.mode = selected

            // صوت نقرة ناعم
            Minecraft.getInstance().player?.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 1.2f)

            val msg = Component.literal("[Easy Building] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(Component.literal("Mode: ${selected.label}").withStyle(ChatFormatting.WHITE))
            Minecraft.getInstance().player?.sendSystemMessage(msg)

            onClose()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        // زر M للتبديل السريع للمرآة Mirror
        if (keyCode == 77) { // M Key
            val all = MirrorMode.entries
            BuildState.mirror = all[(BuildState.mirror.ordinal + 1) % all.size]
            val player = Minecraft.getInstance().player
            if (BuildState.mirror != MirrorMode.OFF && BuildState.mirrorCenter == null && player != null) {
                BuildState.mirrorCenter = player.blockPosition()
            }
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }
}
