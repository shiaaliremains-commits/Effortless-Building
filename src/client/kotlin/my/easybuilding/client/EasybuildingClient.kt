package my.easybuilding.client

import com.mojang.blaze3d.platform.InputConstants
import kotlin.math.abs
import kotlin.math.sin
import my.easybuilding.BuildMode
import my.easybuilding.BuildPayload
import my.easybuilding.BuildState
import my.easybuilding.Mirror
import my.easybuilding.MirrorMode
import my.easybuilding.ShapeGen
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.ChatFormatting
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

object EasybuildingClient : ClientModInitializer {
    private const val MOD_ID = "easybuilding"
    private const val MAX_PER_BLOCK_PREVIEW = 400

    private lateinit var menuKey: KeyMapping
    private var firstPoint: BlockPos? = null
    private var lastClick = 0L
    private var lastKey: String? = null
    private var cachedPositions: List<BlockPos>? = null
    private var cachedTarget: BlockPos? = null

    override fun onInitializeClient() {
        val category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"))
        menuKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping("key.easybuilding.menu", InputConstants.KEY_B, category)
        )

        // 1. عند النقر على بلوكة صلبة (تحديد النقطة الأولى أو التوصيل ببلوكة ثانية) ومنع وضع بلوكة ماين كرافت العادية
        UseBlockCallback.EVENT.register { player, level, hand, hit ->
            val mode = BuildState.mode
            if (mode == BuildMode.NORMAL || hand != InteractionHand.MAIN_HAND || player.mainHandItem.item !is BlockItem) {
                return@register InteractionResult.PASS
            }

            if (level.isClientSide) {
                handleInteraction(player, Minecraft.getInstance(), hit)
            }
            InteractionResult.SUCCESS
        }

        // 2. عند النقر بالهواء للنقطة الثانية (لبناء المربع أو الجدار أو الخط بالجو)
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            val player = client.player ?: return@register
            val mode = BuildState.mode
            if (!isScreenOpen(client) && mode != BuildMode.NORMAL && player.mainHandItem.item is BlockItem) {
                while (client.options.keyUse.consumeClick()) {
                    val hit = client.hitResult
                    // لا يقبل النقر بالهواء إلا إذا كانت النقطة الأولى محددة مسبقاً على بلوكة
                    if (firstPoint != null && (hit == null || hit.type != HitResult.Type.BLOCK)) {
                        handleInteraction(player, client, null)
                    }
                }
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (menuKey.consumeClick()) {
                if (client.player != null) openScreen(ModeMenuScreen())
            }
            updatePreview(client)
            drawMirrorPlane(client)
        }
    }

    private fun handleInteraction(player: Player, client: Minecraft, hit: BlockHitResult?) {
        val mode = BuildState.mode
        if (mode == BuildMode.NORMAL) return

        val now = System.currentTimeMillis()
        if (now - lastClick < 250) return
        lastClick = now

        // Shift + Right Click = إلغاء التحديد
        if (player.isShiftKeyDown) {
            if (firstPoint != null) {
                firstPoint = null
                resetPreview()
                chat(player, "Selection cancelled")
            }
            return
        }

        val first = firstPoint
        if (first == null) {
            // النقطة الأولى: إجباري على بلوكة صلبة فقط
            if (hit != null && hit.type == HitResult.Type.BLOCK) {
                firstPoint = hit.blockPos.relative(hit.direction)
                val tip = if (mode == BuildMode.WALL) "First point set. Look up/down to set the height, then right-click"
                else "First point set. Aim at second point (or air) and right-click"
                chat(player, "$tip (sneak = cancel)")
            }
        } else {
            // النقطة الثانية: سواء كانت على بلوكة ثانية أو بالهواء المفتوح
            val target = cachedTarget ?: getTarget(player, client, first, mode)
            sendBuild(BuildPayload.BUILD, mode, first, target, player)
            firstPoint = null
            resetPreview()
        }
    }

    /** حساب النقطة الثانية: إن كانت على بلوكة ياخذها، وإن كانت بالهواء يحسب مكانها بالجو لأي نمط (مربع، جدار، خط، إلخ) */
    private fun getTarget(player: Player, client: Minecraft, first: BlockPos, mode: BuildMode): BlockPos {
        val hit = client.hitResult
        // 1. إذا مأشر على بلوكة صلبة (للتوصيل بين الأماكن)
        if (hit is BlockHitResult && hit.type == HitResult.Type.BLOCK) {
            return hit.blockPos.relative(hit.direction)
        }

        // 2. إذا نمط جدار بالهواء
        if (mode == BuildMode.WALL) {
            val w = wallTarget(player, first)
            if (w != null) return w
        }

        // 3. لباقي الأنماط (المربع Box، الخط Line، السطح، إلخ) في الهواء:
        val eye = player.eyePosition
        val dir = player.lookAngle
        val distToFirst = eye.distanceTo(Vec3(first.x + 0.5, first.y + 0.5, first.z + 0.5))
        val dist = distToFirst.coerceIn(5.0, 35.0)

        val airPos = eye.add(dir.scale(dist))
        return BlockPos(Mth.floor(airPos.x), Mth.floor(airPos.y), Mth.floor(airPos.z))
    }

    private fun wallTarget(player: Player, first: BlockPos): BlockPos? {
        val eye = player.eyePosition
        val dir = player.lookAngle
        val alongX = abs(dir.x) > abs(dir.z)
        val planeCoord = if (alongX) first.x + 0.5 else first.z + 0.5
        val eyeCoord = if (alongX) eye.x else eye.z
        val dirCoord = if (alongX) dir.x else dir.z
        if (abs(dirCoord) < 1.0E-4) return null
        val t = (planeCoord - eyeCoord) / dirCoord
        if (t <= 0.0 || t > 200.0) return null
        return BlockPos(
            Mth.floor(eye.x + dir.x * t),
            Mth.floor(eye.y + dir.y * t),
            Mth.floor(eye.z + dir.z * t)
        )
    }

    private fun isScreenOpen(client: Minecraft): Boolean {
        val mc = client
        val gui = runCatching { mc.javaClass.getField("gui").get(mc) }.getOrNull()
        if (gui != null) {
            val screenFromGui = runCatching {
                val method = gui.javaClass.methods.firstOrNull { it.name == "screen" && it.parameterCount == 0 }
                method?.invoke(gui)
            }.getOrNull()
            if (screenFromGui != null) return true
        }
        val screenFromMc = runCatching {
            val method = mc.javaClass.methods.firstOrNull { it.name == "screen" && it.parameterCount == 0 }
            if (method != null) method.invoke(mc)
            else mc.javaClass.getField("screen").get(mc)
        }.getOrNull()
        return screenFromMc != null
    }

    private fun chat(player: Player, text: String) {
        val msg = Component.literal("[Easy Building] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
            .append(Component.literal(text).withStyle(ChatFormatting.WHITE))
        player.sendSystemMessage(msg)
    }

    private fun sendBuild(action: Int, mode: BuildMode, a: BlockPos, b: BlockPos, player: Player) {
        val mirror = BuildState.mirror
        if (mirror != MirrorMode.OFF && BuildState.mirrorCenter == null) BuildState.mirrorCenter = player.blockPosition()
        val center = BuildState.mirrorCenter ?: BlockPos.ZERO
        ClientPlayNetworking.send(BuildPayload(action, mode.ordinal, a, b, mirror.ordinal, center))
    }

    private fun resetPreview() {
        lastKey = null
        cachedPositions = null
        cachedTarget = null
    }

    private fun withMirror(list: List<BlockPos>?): List<BlockPos>? {
        if (list == null) return null
        val m = BuildState.mirror
        val c = BuildState.mirrorCenter
        if (m == MirrorMode.OFF || c == null) return list
        return Mirror.apply(list, m, c)
    }

    private fun updatePreview(client: Minecraft) {
        val player = client.player
        val first = firstPoint
        val mode = BuildState.mode
        if (player == null || first == null || mode == BuildMode.NORMAL || player.mainHandItem.item !is BlockItem) {
            if (player == null) firstPoint = null
            resetPreview()
            return
        }

        val target = getTarget(player, client, first, mode)

        val key = "${mode.ordinal}:$first:$target:${BuildState.mirror}:${BuildState.mirrorCenter}"
        if (key != lastKey) {
            lastKey = key
            cachedTarget = target
            cachedPositions = withMirror(ShapeGen.generate(mode, first, target))
            sendBuild(BuildPayload.PREVIEW, mode, first, target, player)
        }

        val shown = cachedTarget
        if (shown != null) drawPreview(first, shown)
    }

    private fun argb(alpha: Int, rgb: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)

    private fun blockBox(p: BlockPos): AABB =
        AABB(p.x.toDouble(), p.y.toDouble(), p.z.toDouble(), p.x + 1.0, p.y + 1.0, p.z + 1.0).inflate(0.003)

    private fun drawPreview(first: BlockPos, target: BlockPos) {
        val pulse = 0.5 + 0.5 * sin(System.currentTimeMillis() / 220.0)
        val fill = argb((35 + 70 * pulse).toInt(), 0xFFFFFF)
        val line = argb((170 + 85 * pulse).toInt(), 0xFFFFFF)
        val list = cachedPositions

        if (list == null) {
            val box = AABB(
                minOf(first.x, target.x).toDouble(), minOf(first.y, target.y).toDouble(), minOf(first.z, target.z).toDouble(),
                maxOf(first.x, target.x) + 1.0, maxOf(first.y, target.y) + 1.0, maxOf(first.z, target.z) + 1.0
            )
            Gizmos.cuboid(box, GizmoStyle.fill(argb(40, 0xFF3030))).persistForMillis(100)
            Gizmos.cuboid(box, GizmoStyle.stroke(argb(255, 0xFF3030))).persistForMillis(100)
            return
        }

        if (list.size <= MAX_PER_BLOCK_PREVIEW) {
            for (p in list) {
                val box = blockBox(p)
                Gizmos.cuboid(box, GizmoStyle.fill(fill)).persistForMillis(100)
                Gizmos.cuboid(box, GizmoStyle.stroke(line)).persistForMillis(100)
            }
        } else {
            var minX = Int.MAX_VALUE
            var minY = Int.MAX_VALUE
            var minZ = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            var maxY = Int.MIN_VALUE
            var maxZ = Int.MIN_VALUE
            for (p in list) {
                minX = minOf(minX, p.x); minY = minOf(minY, p.y); minZ = minOf(minZ, p.z)
                maxX = maxOf(maxX, p.x); maxY = maxOf(maxY, p.y); maxZ = maxOf(maxZ, p.z)
            }
            val box = AABB(minX.toDouble(), minY.toDouble(), minZ.toDouble(), maxX + 1.0, maxY + 1.0, maxZ + 1.0)
            Gizmos.cuboid(box, GizmoStyle.fill(fill)).persistForMillis(100)
            Gizmos.cuboid(box, GizmoStyle.stroke(line)).persistForMillis(100)
        }
    }

    private fun drawMirrorPlane(client: Minecraft) {
        val player = client.player ?: return
        val m = BuildState.mirror
        val c = BuildState.mirrorCenter ?: return
        if (m == MirrorMode.OFF || BuildState.mode == BuildMode.NORMAL || player.mainHandItem.item !is BlockItem) return

        val y0 = Mth.floor(player.y) - 6.0
        val y1 = Mth.floor(player.y) + 14.0
        val half = 24.0
        val fill = GizmoStyle.fill(argb(34, 0x00E5FF))
        val line = GizmoStyle.stroke(argb(200, 0x00E5FF))

        if (m == MirrorMode.X || m == MirrorMode.BOTH) {
            val x = c.x + 0.5
            val box = AABB(x - 0.02, y0, c.z - half, x + 0.02, y1, c.z + half + 1.0)
            Gizmos.cuboid(box, fill).persistForMillis(100)
            Gizmos.cuboid(box, line).persistForMillis(100)
        }
        if (m == MirrorMode.Z || m == MirrorMode.BOTH) {
            val z = c.z + 0.5
            val box = AABB(c.x - half, y0, z - 0.02, c.x + half + 1.0, y1, z + 0.02)
            Gizmos.cuboid(box, fill).persistForMillis(100)
            Gizmos.cuboid(box, line).persistForMillis(100)
        }
    }

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
