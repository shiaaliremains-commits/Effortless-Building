package my.easybuilding.client

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ceil
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
     * RADIAL MENU SETTINGS
     * ============================================================
     */

    private val innerR = 34.0
    private val outerR = 88.0

    // المسافة الصغيرة بين القطاعات
    private val sliceGap = 0.035

    // مقدار تكبير القطاع عند Hover
    private val hoverExpand = 4.0


    override fun isPauseScreen(): Boolean = false


    /*
     * ============================================================
     * SLICE DETECTION
     * ============================================================
     *
     * نفس هندسة العجلة المستخدمة بالرسم.
     * القطاع رقم 0 يبدأ من الأعلى.
     */

    private fun getSliceAt(mouseX: Double, mouseY: Double): Int {

        val cx = width / 2.0
        val cy = height / 2.0

        val dx = mouseX - cx
        val dy = mouseY - cy

        val distance = sqrt(dx * dx + dy * dy)

        /*
         * السماح بقليل من المساحة الإضافية حتى يكون الضغط مريح.
         */
        if (distance < innerR - 3.0 || distance > outerR + hoverExpand + 3.0) {
            return -1
        }

        val total = BuildMode.entries.size

        if (total <= 0) {
            return -1
        }

        val angleStep = (Math.PI * 2.0) / total

        /*
         * atan2:
         *
         *        -PI/2
         *           ↑
         *
         * نريد هذا المكان يكون القطاع 0.
         */

        var angle = atan2(dy, dx)

        angle -= -Math.PI / 2.0

        while (angle < 0.0) {
            angle += Math.PI * 2.0
        }

        while (angle >= Math.PI * 2.0) {
            angle -= Math.PI * 2.0
        }

        /*
         * تدوير التقسيم نصف قطاع حتى يكون
         * القطاع الأول متمركز تماماً في الأعلى.
         */
        val index =
            floor((angle + angleStep / 2.0) / angleStep).toInt()

        return index.coerceIn(0, total - 1)
    }


    /*
     * ============================================================
     * MAIN RENDER
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
         * DRAW RADIAL SLICES
         * ========================================================
         */

        for (i in 0 until total) {

            val mode = modes[i]

            val isCurrent =
                BuildState.mode == mode

            val isHovered =
                hovered == i


            /*
             * الألوان
             */

            val color = when {

                isHovered ->
                    0xF55DBBFF.toInt()

                isCurrent ->
                    0xEE147FC4.toInt()

                else ->
                    0xE13A3630.toInt()
            }


            /*
             * منتصف القطاع
             */

            val aMid =
                -Math.PI / 2.0 +
                        (i * angleStep)


            /*
             * حدود القطاع
             */

            val a1 =
                aMid -
                        angleStep / 2.0 +
                        sliceGap

            val a2 =
                aMid +
                        angleStep / 2.0 -
                        sliceGap


            /*
             * Hover يجعل القطاع يبرز قليلاً.
             */

            val currentOuterR =
                if (isHovered) {
                    outerR + hoverExpand
                } else {
                    outerR
                }


            /*
             * رسم القطاع
             */

            drawSolidSector(
                graphics,
                cx,
                cy,
                innerR,
                currentOuterR,
                a1,
                a2,
                color
            )


            /*
             * ====================================================
             * ICON
             * ====================================================
             */

            val midR =
                (innerR + currentOuterR) / 2.0

            val iconX =
                (cx + cos(aMid) * midR).toInt()

            val iconY =
                (cy + sin(aMid) * midR).toInt()


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
         * HOVER LABEL
         * ========================================================
         */

        if (hovered in modes.indices) {

            val label =
                modes[hovered].label

            drawHoverLabel(
                graphics,
                cx,
                cy,
                label
            )
        }


        /*
         * ========================================================
         * BOTTOM CONTROLS
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
     * RADIAL SECTOR
     * ============================================================
     *
     * Scanline محسّن:
     *
     * - بدون step 2 القديم
     * - بدون فراغات بين الخطوط
     * - حساب حدود القطاع بشكل مباشر
     * - الحواف أنظف بكثير
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
            ceil(cy + rOut).toInt()


        for (y in minY..maxY) {

            /*
             * نستخدم منتصف البكسل.
             */
            val py =
                (y + 0.5) - cy

            val py2 =
                py * py


            if (py2 > rOut * rOut) {
                continue
            }


            /*
             * حدود الدائرة الخارجية.
             */

            val outerX =
                sqrt(
                    max(
                        0.0,
                        rOut * rOut - py2
                    )
                )


            /*
             * نجرب نقاط X كثيرة بدقة أعلى.
             *
             * هذا أفضل بكثير من x += 1.5 القديم.
             */

            val sampleStep = 0.5

            var left =
                Double.POSITIVE_INFINITY

            var right =
                Double.NEGATIVE_INFINITY


            var x =
                -outerX


            while (x <= outerX) {

                val dist2 =
                    x * x + py2


                /*
                 * داخل الحلقة؟
                 */

                if (
                    dist2 >= (rIn * rIn) &&
                    dist2 <= (rOut * rOut)
                ) {

                    var angle =
                        atan2(py, x)


                    /*
                     * تحويل الزاوية إلى نفس نظام
                     * getSliceAt().
                     */

                    while (angle < a1) {
                        angle += Math.PI * 2.0
                    }

                    while (angle >= a1 + Math.PI * 2.0) {
                        angle -= Math.PI * 2.0
                    }


                    /*
                     * داخل القطاع.
                     */

                    if (angle <= a2) {

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


                x += sampleStep
            }


            /*
             * رسم الخط إذا وجد.
             */

            if (
                left.isFinite() &&
                right.isFinite() &&
                right > left
            ) {

                graphics.fill(
                    floor(cx + left).toInt(),
                    y,
                    ceil(cx + right + 1.0).toInt(),
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

        /*
         * دائرة/منطقة الوسط.
         *
         * نستخدم عدة طبقات حتى تظهر أنعم.
         */

        graphics.fill(
            cx - 30,
            cy - 30,
            cx + 30,
            cy + 30,
            0x50101010
        )

        graphics.fill(
            cx - 27,
            cy - 27,
            cx + 27,
            cy + 27,
            0xAA182015.toInt()
        )

        graphics.fill(
            cx - 24,
            cy - 24,
            cx + 24,
            cy + 24,
            0xCC20291B.toInt()
        )


        /*
         * +
         */

        graphics.fill(
            cx - 2,
            cy - 13,
            cx + 3,
            cy + 14,
            0xFFFFFFFF.toInt()
        )

        graphics.fill(
            cx - 13,
            cy - 2,
            cx + 14,
            cy + 3,
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

        val x =
            (cx + outerR + 16).toInt()

        val y =
            (cy - 6).toInt()


        /*
         * خلفية صغيرة للنص.
         */

        graphics.fill(
            x - 7,
            y - 5,
            x + textWidth + 7,
            y + 12,
            0xA820201E.toInt()
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
     * MODE ICONS
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
             * أي mode إضافي
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
     * CUBE ICON
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

        /*
         * Top
         */

        graphics.fill(
            x - 4,
            y - 5,
            x + 5,
            y - 2,
            top
        )


        /*
         * Left
         */

        graphics.fill(
            x - 4,
            y - 2,
            x,
            y + 5,
            left
        )


        /*
         * Right
         */

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
         * Mirror background
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
         * Undo background
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
     * MOUSE CLICK
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
             * أولاً نفحص الـ radial menu.
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
                 * تغيير الـ mode
                 */

                BuildState.mode =
                    selected


                /*
                 * صوت الضغط
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
                        .literal("[Easy Building] ")
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
                    ?.sendSystemMessage(msg)


                /*
                 * إغلاق القائمة
                 */

                onClose()

                return true
            }


            /*
             * ====================================================
             * BOTTOM BUTTONS
             * ====================================================
             */

            if (
                my >= height - 26 &&
                my <= height - 8
            ) {

                /*
                 * Undo
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
                 * Mirror
                 */

                if (
                    mx >= cx - 140 &&
                    mx <= cx - 10
                ) {

                    cycleMirror()

                    return true
                }
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
                (BuildState.mirror.ordinal + 1)
                        % all.size
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
         * ESC
         * B
         */

        if (
            key == 256 ||
            key == 66
        ) {

            onClose()

            return true
        }


        /*
         * M = Mirror
         */

        if (key == 77) {

            cycleMirror()

            return true
        }


        return super.keyPressed(event)
    }
}
