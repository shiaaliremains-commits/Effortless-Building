package my.easybuilding.client

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
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

    /*
     * ============================================================
     * RADIAL MENU
     * ============================================================
     *
     * الحجم يعتمد على حجم شاشة الـ GUI حتى لا يختلف
     * مكان الـ click عن مكان الرسم.
     */

    private val outerR: Double
        get() = min(width, height) * 0.37

    private val innerR: Double
        get() = outerR * 0.31

    private val centerR: Double
        get() = outerR * 0.30

    private val sliceGap = 0.045

    private val hoverExpand = outerR * 0.025


    override fun isPauseScreen(): Boolean = false


    /*
     * ============================================================
     * GET SLICE
     * ============================================================
     *
     * مهم جداً:
     *
     * ما نعتمد على outerR حتى لا يصير اختلاف بين
     * حجم الرسم وحجم الـ mouse coordinates.
     *
     * نستخدم الزاوية فقط بعد التأكد أن الماوس خارج المركز.
     */

    private fun getSliceAt(
        mouseX: Double,
        mouseY: Double
    ): Int {

        val cx = width / 2.0
        val cy = height / 2.0

        val dx = mouseX - cx
        val dy = mouseY - cy

        val distance = sqrt(
            dx * dx + dy * dy
        )

        /*
         * داخل زر الوسط = لا يوجد sector.
         */

        if (distance < innerR * 0.85) {
            return -1
        }

        val total = BuildMode.entries.size

        if (total == 0) {
            return -1
        }

        val angleStep =
            (Math.PI * 2.0) / total

        /*
         * زاوية الماوس.
         */

        var angle =
            atan2(dy, dx)

        /*
         * نخلي الساعة 12 = صفر.
         */

        angle += Math.PI / 2.0

        /*
         * تطبيع الزاوية إلى 0..2PI
         */

        while (angle < 0.0) {
            angle += Math.PI * 2.0
        }

        while (angle >= Math.PI * 2.0) {
            angle -= Math.PI * 2.0
        }

        /*
         * كل sector يتمركز حول زاويته.
         */

        return floor(
            (angle + angleStep / 2.0) /
                    angleStep
        ).toInt().coerceIn(
            0,
            total - 1
        )
    }


    /*
     * ============================================================
     * RENDER
     * ============================================================
     */

    override fun extractRenderState(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        delta: Float
    ) {

        super.extractRenderState(
            graphics,
            mouseX,
            mouseY,
            delta
        )

        val cx = width / 2.0
        val cy = height / 2.0

        val modes = BuildMode.entries
        val total = modes.size

        if (total == 0) {
            return
        }

        val angleStep =
            (Math.PI * 2.0) / total

        val hovered =
            getSliceAt(
                mouseX.toDouble(),
                mouseY.toDouble()
            )


        /*
         * ========================================================
         * SECTORS
         * ========================================================
         */

        for (i in 0 until total) {

            val mode =
                modes[i]

            val isCurrent =
                BuildState.mode == mode

            val isHovered =
                hovered == i


            /*
             * اللون
             */

            val color = when {

                isHovered ->
                    0xF557B9F4.toInt()

                isCurrent ->
                    0xEE0C78BD.toInt()

                else ->
                    0xE13A3630.toInt()
            }


            /*
             * زاوية القطاع
             */

            val aMid =
                -Math.PI / 2.0 +
                        i * angleStep


            val a1 =
                aMid -
                        angleStep / 2.0 +
                        sliceGap

            val a2 =
                aMid +
                        angleStep / 2.0 -
                        sliceGap


            /*
             * القطاع المحدد يطلع للخارج قليلاً.
             */

            val currentOuter =
                if (isHovered) {
                    outerR + hoverExpand
                } else {
                    outerR
                }


            drawSolidSector(
                graphics,
                cx,
                cy,
                innerR,
                currentOuter,
                a1,
                a2,
                color
            )


            /*
             * ====================================================
             * ICON
             * ====================================================
             */

            val iconRadius =
                (innerR + currentOuter) / 2.0

            val iconX =
                (
                    cx +
                            cos(aMid) *
                            iconRadius
                    ).toInt()

            val iconY =
                (
                    cy +
                            sin(aMid) *
                            iconRadius
                    ).toInt()


            drawModeIcon(
                graphics,
                mode,
                iconX,
                iconY
            )
        }


        /*
         * ========================================================
         * CENTER
         * ========================================================
         */

        drawCenter(
            graphics,
            cx.toInt(),
            cy.toInt()
        )


        /*
         * ========================================================
         * LABEL
         * ========================================================
         */

        if (hovered in modes.indices) {

            drawHoverLabel(
                graphics,
                cx,
                cy,
                modes[hovered].label
            )
        }


        /*
         * ========================================================
         * BOTTOM BUTTONS
         * ========================================================
         */

        drawBottomPills(
            graphics,
            cx.toInt(),
            height - 26
        )
    }


    /*
     * ============================================================
     * SECTOR DRAWING
     * ============================================================
     *
     * Scanline بدقة عالية.
     *
     * الخطوة 0.35 بدلاً من 1.5 حتى تختفي الفراغات.
     */

    private fun drawSolidSector(
        graphics: GuiGraphicsExtractor,
        cx: Double,
        cy: Double,
        rIn: Double,
        rOut: Double,
        a1: Double,
        a2: Double,
        color: Int
    ) {

        val minY =
            floor(cy - rOut).toInt()

        val maxY =
            floor(cy + rOut).toInt()


        for (y in minY..maxY) {

            val py =
                y + 0.5 - cy

            val py2 =
                py * py


            if (py2 > rOut * rOut) {
                continue
            }


            val outerX =
                sqrt(
                    max(
                        0.0,
                        rOut * rOut - py2
                    )
                )


            var left =
                Double.POSITIVE_INFINITY

            var right =
                Double.NEGATIVE_INFINITY


            var x =
                -outerX

            val step =
                0.35


            while (x <= outerX) {

                val distance2 =
                    x * x + py2


                if (
                    distance2 >= rIn * rIn &&
                    distance2 <= rOut * rOut
                ) {

                    var angle =
                        atan2(py, x)


                    /*
                     * نخلي الزاوية بنفس مجال القطاع.
                     */

                    while (angle < a1) {
                        angle += Math.PI * 2.0
                    }

                    while (angle > a2) {
                        angle -= Math.PI * 2.0
                    }


                    if (
                        angle >= a1 &&
                        angle <= a2
                    ) {

                        left =
                            min(
                                left,
                                x
                            )

                        right =
                            max(
                                right,
                                x
                            )
                    }
                }


                x += step
            }


            if (
                left.isFinite() &&
                right.isFinite() &&
                right > left
            ) {

                graphics.fill(
                    floor(
                        cx + left
                    ).toInt(),

                    y,

                    floor(
                        cx + right + 1.0
                    ).toInt(),

                    y + 1,

                    color
                )
            }
        }
    }


    /*
     * ============================================================
     * CENTER BUTTON
     * ============================================================
     */

    private fun drawCenter(
        graphics: GuiGraphicsExtractor,
        cx: Int,
        cy: Int
    ) {

        val r =
            centerR.toInt()


        /*
         * ظل خارجي
         */

        graphics.fill(
            cx - r - 5,
            cy - r - 5,
            cx + r + 5,
            cy + r + 5,
            0x55101010
        )


        /*
         * الطبقة الخارجية
         */

        graphics.fill(
            cx - r,
            cy - r,
            cx + r,
            cy + r,
            0xAA171914.toInt()
        )


        /*
         * الطبقة الداخلية
         */

        val inner =
            max(
                8,
                r - 7
            )

        graphics.fill(
            cx - inner,
            cy - inner,
            cx + inner,
            cy + inner,
            0xD8202819.toInt()
        )


        /*
         * علامة +
         *
         * أصغر ومتناسقة مع المركز.
         */

        val plus =
            max(
                5,
                (r * 0.28).toInt()
            )

        val plusThickness =
            max(
                2,
                (r * 0.10).toInt()
            )


        graphics.fill(
            cx - plusThickness,
            cy - plus,
            cx + plusThickness + 1,
            cy + plus + 1,
            0xFFFFFFFF.toInt()
        )


        graphics.fill(
            cx - plus,
            cy - plusThickness,
            cx + plus + 1,
            cy + plusThickness + 1,
            0xFFFFFFFF.toInt()
        )
    }


    /*
     * ============================================================
     * HOVER LABEL
     * ============================================================
     */

    private fun drawHoverLabel(
        graphics: GuiGraphicsExtractor,
        cx: Double,
        cy: Double,
        label: String
    ) {

        val textWidth =
            font.width(label)


        /*
         * مكان النص بالنسبة للعجلة.
         */

        val x =
            (
                cx +
                        outerR +
                        18
                ).toInt()

        val y =
            (
                cy - 6
                ).toInt()


        /*
         * الخلفية
         */

        graphics.fill(
            x - 8,
            y - 6,
            x + textWidth + 8,
            y + 12,
            0xB820201D.toInt()
        )


        graphics.text(
            font,
            Component.literal(label),
            x,
            y,
            0xFFFFFFFF.toInt()
        )
    }


    /*
     * ============================================================
     * ICONS
     * ============================================================
     */

    private fun drawModeIcon(
        graphics: GuiGraphicsExtractor,
        mode: BuildMode,
        x: Int,
        y: Int
    ) {

        when (mode.name) {

            /*
             * NORMAL
             */

            "NORMAL" -> {

                drawCube(
                    graphics,
                    x,
                    y,
                    0xFFFFFFFF.toInt(),
                    0xFFCCCCCC.toInt(),
                    0xFF999999.toInt()
                )
            }


            /*
             * LINE
             */

            "LINE" -> {

                drawCube(
                    graphics,
                    x - 7,
                    y,
                    0xFFFFFFFF.toInt(),
                    0xFFCCCCCC.toInt(),
                    0xFF999999.toInt()
                )

                drawCube(
                    graphics,
                    x,
                    y,
                    0xFFFFFFFF.toInt(),
                    0xFFCCCCCC.toInt(),
                    0xFF999999.toInt()
                )

                drawCube(
                    graphics,
                    x + 7,
                    y,
                    0xFFFFFFFF.toInt(),
                    0xFFCCCCCC.toInt(),
                    0xFF999999.toInt()
                )
            }


            /*
             * WALL
             */

            "WALL" -> {

                drawCube(
                    graphics,
                    x,
                    y - 7,
                    0xFFFFFFFF.toInt(),
                    0xFFCCCCCC.toInt(),
                    0xFF999999.toInt()
                )

                drawCube(
                    graphics,
                    x,
                    y,
                    0xFFFFFFFF.toInt(),
                    0xFFCCCCCC.toInt(),
                    0xFF999999.toInt()
                )

                drawCube(
                    graphics,
                    x,
                    y + 7,
                    0xFFFFFFFF.toInt(),
                    0xFFCCCCCC.toInt(),
                    0xFF999999.toInt()
                )
            }


            /*
             * FLOOR
             */

            "FLOOR" -> {

                graphics.fill(
                    x - 9,
                    y - 3,
                    x + 9,
                    y + 2,
                    0xFFFFFFFF.toInt()
                )

                graphics.fill(
                    x - 9,
                    y + 2,
                    x + 9,
                    y + 5,
                    0xFF888888.toInt()
                )
            }


            /*
             * BOX
             */

            "BOX" -> {

                graphics.fill(
                    x - 7,
                    y - 7,
                    x + 7,
                    y + 7,
                    0xFFFFFFFF.toInt()
                )

                graphics.fill(
                    x - 5,
                    y - 5,
                    x + 5,
                    y + 5,
                    0xDD2A2520.toInt()
                )
            }


            /*
             * أي Mode إضافي
             */

            else -> {

                drawCube(
                    graphics,
                    x,
                    y,
                    0xFF88D8FF.toInt(),
                    0xFF44A8E0.toInt(),
                    0xFF2278B0.toInt()
                )
            }
        }
    }


    /*
     * ============================================================
     * CUBE
     * ============================================================
     */

    private fun drawCube(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        top: Int,
        left: Int,
        right: Int
    ) {

        graphics.fill(
            x - 4,
            y - 5,
            x + 5,
            y - 2,
            top
        )

        graphics.fill(
            x - 4,
            y - 2,
            x,
            y + 5,
            left
        )

        graphics.fill(
            x,
            y - 2,
            x + 5,
            y + 5,
            right
        )
    }


    /*
     * ============================================================
     * BOTTOM BUTTONS
     * ============================================================
     */

    private fun drawBottomPills(
        graphics: GuiGraphicsExtractor,
        cx: Int,
        y: Int
    ) {

        val mirrorText =
            "[M] Mirror: ${BuildState.mirror.label}"

        val undoText =
            "↩ Undo (Ctrl+Z)"


        /*
         * Mirror
         */

        graphics.fill(
            cx - 140,
            y,
            cx - 10,
            y + 18,
            0xAA22201C.toInt()
        )

        graphics.centeredText(
            font,
            Component.literal(mirrorText),
            cx - 75,
            y + 5,
            0xFFFFFFFF.toInt()
        )


        /*
         * Undo
         */

        graphics.fill(
            cx + 10,
            y,
            cx + 140,
            y + 18,
            0xAA22201C.toInt()
        )

        graphics.centeredText(
            font,
            Component.literal(undoText),
            cx + 75,
            y + 5,
            0xFFFFFFFF.toInt()
        )
    }


    /*
     * ============================================================
     * MOUSE
     * ============================================================
     */

    override fun mouseClicked(
        event: MouseButtonEvent,
        doubleClick: Boolean
    ): Boolean {

        val mx =
            event.x()

        val my =
            event.y()

        val cx =
            width / 2.0


        /*
         * ========================================================
         * LEFT CLICK
         * ========================================================
         */

        if (event.button() == 0) {

            /*
             * أولاً نتحقق من أزرار الأسفل.
             *
             * هذا يمنع الـ radial selection من أكل
             * ضغطات Mirror / Undo.
             */

            if (
                my >= height - 26 &&
                my <= height - 8
            ) {

                /*
                 * UNDO
                 */

                if (
                    mx >= cx + 10 &&
                    mx <= cx + 140
                ) {

                    EasybuildingClient.sendUndo()

                    onClose()

                    return true
                }


                /*
                 * MIRROR
                 */

                if (
                    mx >= cx - 140 &&
                    mx <= cx - 10
                ) {

                    cycleMirror()

                    return true
                }
            }


            /*
             * ====================================================
             * RADIAL SELECTION
             * ====================================================
             */

            val clickedSlice =
                getSliceAt(
                    mx,
                    my
                )


            if (
                clickedSlice >= 0 &&
                clickedSlice < BuildMode.entries.size
            ) {

                val selected =
                    BuildMode.entries[clickedSlice]


                /*
                 * تغيير الـ Mode
                 */

                BuildState.mode =
                    selected


                /*
                 * صوت
                 */

                Minecraft
                    .getInstance()
                    .player
                    ?.playSound(
                        SoundEvents.UI_BUTTON_CLICK.value(),
                        1.0f,
                        1.2f
                    )


                /*
                 * رسالة
                 */

                val msg =
                    Component
                        .literal(
                            "[Easy Building] "
                        )
                        .withStyle(
                            ChatFormatting.AQUA,
                            ChatFormatting.BOLD
                        )
                        .append(
                            Component
                                .literal(
                                    "Mode: ${selected.label}"
                                )
                                .withStyle(
                                    ChatFormatting.WHITE
                                )
                        )


                Minecraft
                    .getInstance()
                    .player
                    ?.sendSystemMessage(
                        msg
                    )


                /*
                 * إغلاق القائمة
                 */

                onClose()

                return true
            }
        }


        return super.mouseClicked(
            event,
            doubleClick
        )
    }


    /*
     * ============================================================
     * MIRROR
     * ============================================================
     */

    private fun cycleMirror() {

        val all =
            MirrorMode.entries


        BuildState.mirror =
            all[
                (
                    BuildState.mirror.ordinal + 1
                ) % all.size
            ]


        val player =
            Minecraft
                .getInstance()
                .player


        if (
            BuildState.mirror != MirrorMode.OFF &&
            BuildState.mirrorCenter == null &&
            player != null
        ) {

            BuildState.mirrorCenter =
                player.blockPosition()
        }
    }


    /*
     * ============================================================
     * KEYBOARD
     * ============================================================
     */

    override fun keyPressed(
        event: KeyEvent
    ): Boolean {

        val key =
            event.key()


        /*
         * ESC / B
         */

        if (
            key == 256 ||
            key == 66
        ) {

            onClose()

            return true
        }


        /*
         * M
         */

        if (key == 77) {

            cycleMirror()

            return true
        }


        return super.keyPressed(
            event
        )
    }
}
