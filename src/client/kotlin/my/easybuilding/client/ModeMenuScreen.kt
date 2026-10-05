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

    // الأبعاد الهندسية للمضلع الثماني
    private val innerR = 32.0
    private val outerR = 86.0

    override fun isPauseScreen(): Boolean = false

    /** حساب رقم الشريحة بدقة متناهية بناءً على زاوية ومسافة الماوس من المركز */
    private fun getSliceAt(mouseX: Double, mouseY: Double): Int {
        val cx = width / 2.0
        val cy = height / 2.0
        val dx = mouseX - cx
        val dy = mouseY - cy
        val dist = sqrt(dx * dx + dy * dy)

        // إذا كان الماوس داخل الحلقة المخصصة للشرائح
        if (dist in (innerR - 4.0)..(outerR + 12.0)) {
            val total = BuildMode.entries.size
            val angleStep = (2.0 * Math.PI) / total
            var angle = atan2(dy, dx)
            // محاذاة الشريحة الأولى في الأعلى تماماً (الساعة 12)
            var rel = angle - (-Math.PI / 2.0 - angleStep / 2.0)
            while (rel < 0) rel += 2.0 * Math.PI
            while (rel >= 2.0 * Math.PI) rel -= 2.0 * Math.PI
            return (rel / angleStep).toInt().coerceIn(0, total - 1)
        }
        return -1
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)

        val cx = width / 2.0
        val cy = height / 2.0
        val modes = BuildMode.entries
        val total = modes.size
        val angleStep = (2.0 * Math.PI) / total
        val hovered = getSliceAt(mouseX.toDouble(), mouseY.toDouble())

        // 1. رسم الشرائح الهندسية الناعمة (بدون أي تنقيط أو تشوه)
        for (i in 0 until total) {
            val mode = modes[i]
            val isCurrent = BuildState.mode == mode
            val isHovered = hovered == i

            // الألوان تماماً مثل صورة المود الأصلية
            val color = when {
                isHovered -> 0xF554B8F5.toInt() // أزرق سماوي مضيء للشريحة المأشر عليها
                isCurrent -> 0xEE0B76BD.toInt() // أزرق ملوكي عميق للشريحة المختارة
                else -> 0xDE3C3630.toInt()       // رمادي داكن ناعم لباقي الشرائح
            }

            val aMid = -Math.PI / 2.0 + (i * angleStep)
            val a1 = aMid - (angleStep / 2.0) + 0.025
            val a2 = aMid + (angleStep / 2.0) - 0.025
            val rOutCurrent = if (isHovered) outerR + 4.0 else outerR

            // رسم الشريحة كسطح متصل ونظيف
            drawSolidSector(graphics, cx, cy, innerR, rOutCurrent, a1, a2, color)

            // رسم الأيقونة المصغرة داخل كل شريحة
            val midR = (innerR + outerR) / 2.0
            val iconX = (cx + cos(aMid) * midR).toInt()
            val iconY = (cy + sin(aMid) * midR).toInt()
            drawModeIcon(graphics, mode, iconX, iconY)
        }

        // 2. الفتحة الوسطية النظيفة مع علامة +
        graphics.fill((cx - 2).toInt(), (cy - 7).toInt(), (cx + 3).toInt(), (cy + 8).toInt(), 0xDDFFFFFF.toInt())
        graphics.fill((cx - 7).toInt(), (cy - 2).toInt(), (cx + 8).toInt(), (cy + 3).toInt(), 0xDDFFFFFF.toInt())

        // 3. كتابة اسم النمط بالكامل على يمين العجلة عند التأشير (مثل كلمة Normal+ بالصورة)
        if (hovered in modes.indices) {
            val label = modes[hovered].label
            val textX = (cx + outerR + 18).toInt()
            val textY = (cy - 4).toInt()
            graphics.text(font, Component.literal(label), textX, textY, 0xFFFFFFFF.toInt())
        }

        // 4. أزرار التحكم السفلية (Mirror و Undo) بتصميم أنيق
        drawBottomPills(graphics, cx.toInt(), height - 24)
    }

    /** رسم شرائح متصلة وممتلئة بنعومة فائقة وبدون أي فراغات أو تنقيط */
    private fun drawSolidSector(
        graphics: GuiGraphicsExtractor,
        cx: Double, cy: Double,
        rIn: Double, rOut: Double,
        a1: Double, a2: Double,
        color: Int
    ) {
        val yMin = (cy - rOut).toInt()
        val yMax = (cy + rOut).toInt()

        // رسم شريطي أفقي متصل (Scanline) يمنع التشوه والتنقيط نهائياً
        for (y in yMin..yMax step 2) {
            val dy = (y + 1) - cy
            val dySq = dy * dy

            // التحقق من حدود نصف القطر
            if (dySq > rOut * rOut) continue

            val xMaxOut = sqrt(rOut * rOut - dySq)
            val xMinIn = if (dySq < rIn * rIn) sqrt(rIn * rIn - dySq) else 0.0

            var leftX = Double.MAX_VALUE
            var rightX = -Double.MAX_VALUE

            // فحص الخط الأفقي وضمان وقوعه داخل زاوية الشريحة المحددة
            var x = -xMaxOut
            while (x <= xMaxOut) {
                val distSq = x * x + dySq
                if (distSq in (rIn * rIn)..(rOut * rOut)) {
                    var ang = atan2(dy, x)
                    while (ang < a1 - Math.PI) ang += 2.0 * Math.PI
                    while (ang > a1 + Math.PI) ang -= 2.0 * Math.PI
                    if (ang in a1..a2) {
                        if (x < leftX) leftX = x
                        if (x > rightX) rightX = x
                    }
                }
                x += 1.5
            }

            if (leftX <= rightX) {
                graphics.fill(
                    (cx + leftX).toInt(),
                    y,
                    (cx + rightX + 1.0).toInt(),
                    y + 2,
                    color
                )
            }
        }
    }

    /** رسم الأيقونات ثلاثية الأبعاد بوضوح ونظافة تامة */
    private fun drawModeIcon(graphics: GuiGraphicsExtractor, mode: BuildMode, x: Int, y: Int) {
        when (mode.name) {
            "NORMAL" -> {
                drawCube(graphics, x, y, 0xFFFFFFFF.toInt(), 0xFFCCCCCC.toInt(), 0xFF999999.toInt())
            }
            "LINE" -> {
                drawCube(graphics, x - 5, y, 0xFFFFFFFF.toInt(), 0xFFCCCCCC.toInt(), 0xFF999999.toInt())
                drawCube(graphics, x, y, 0xFFFFFFFF.toInt(), 0xFFCCCCCC.toInt(), 0xFF999999.toInt())
                drawCube(graphics, x + 5, y, 0xFFFFFFFF.toInt(), 0xFFCCCCCC.toInt(), 0xFF999999.toInt())
            }
            "WALL" -> {
                drawCube(graphics, x, y - 5, 0xFFFFFFFF.toInt(), 0xFFCCCCCC.toInt(), 0xFF999999.toInt())
                drawCube(graphics, x, y, 0xFFFFFFFF.toInt(), 0xFFCCCCCC.toInt(), 0xFF999999.toInt())
                drawCube(graphics, x, y + 5, 0xFFFFFFFF.toInt(), 0xFFCCCCCC.toInt(), 0xFF999999.toInt())
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
                drawCube(graphics, x, y, 0xFF88D8FF.toInt(), 0xFF44A8E0.toInt(), 0xFF2278B0.toInt())
            }
        }
    }

    private fun drawCube(graphics: GuiGraphicsExtractor, x: Int, y: Int, top: Int, left: Int, right: Int) {
        // وجه المكعب العلوي
        graphics.fill(x - 4, y - 5, x + 5, y - 2, top)
        // الوجه الجانبي الأيسر
        graphics.fill(x - 4, y - 2, x, y + 4, left)
        // الوجه الجانبي الأيمن
        graphics.fill(x, y - 2, x + 5, y + 4, right)
    }

    private fun drawBottomPills(graphics: GuiGraphicsExtractor, cx: Int, y: Int) {
        val mirrorText = "[M] Mirror: ${BuildState.mirror.label}"
        val undoText = "↩ Undo (Ctrl+Z)"

        graphics.fill(cx - 130, y, cx - 10, y + 16, 0xAA22201C.toInt())
        graphics.centeredText(font, Component.literal(mirrorText), cx - 70, y + 4, 0xFFFFFFFF.toInt())

        graphics.fill(cx + 10, y, cx + 130, y + 16, 0xAA22201C.toInt())
        graphics.centeredText(font, Component.literal(undoText), cx + 70, y + 4, 0xFFFFFFFF.toInt())
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x()
        val my = event.y()
        val cx = width / 2.0

        // 1. فحص الشريحة التي تم النقر عليها مباشرة بلحظة النقر
        if (event.button() == 0) {
            val clickedSlice = getSliceAt(mx, my)
            if (clickedSlice in BuildMode.entries.indices) {
                val selected = BuildMode.entries[clickedSlice]
                BuildState.mode = selected

                Minecraft.getInstance().player?.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.2f)
                val msg = Component.literal("[Easy Building] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                    .append(Component.literal("Mode: ${selected.label}").withStyle(ChatFormatting.WHITE))
                Minecraft.getInstance().player?.sendSystemMessage(msg)

                onClose()
                return true
            }

            // 2. فحص النقر على أزرار التراجع أو المرآة السفلية
            if (my in (height - 24).toDouble()..(height - 8).toDouble()) {
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
        if (key == 256 || key == 66) { // زر ESC أو B
            onClose()
            return true
        }
        if (key == 77) { // زر M
            cycleMirror()
            return true
        }
        return super.keyPressed(event)
    }
}
