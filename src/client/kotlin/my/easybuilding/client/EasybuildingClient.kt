package my.easybuilding.client

import com.mojang.blaze3d.platform.InputConstants
import kotlin.math.abs
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
    private const val MAX_AIM = 120.0
    private const val REFRESH_TICKS = 5

    var autoDirection: Boolean = true

    private lateinit var menuKey: KeyMapping
    private lateinit var undoKey: KeyMapping
    private var firstPoint: BlockPos? = null
    private var lastClick = 0L
    private var lastKey: String? = null
    private var rawPositions: List<BlockPos>? = null
    private var placeable: List<BlockPos> = emptyList()
    private var affordable = 0
    private var tooBig = false
    private var cachedTarget: BlockPos? = null
    private var tickCounter = 0
    private var wallAxis = 2
    private var spaceAxis = 1
    private var screenProbe: ((Minecraft) -> Any?)? = null

    override fun onInitializeClient() {
        val category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"))
        menuKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping("key.easybuilding.menu", InputConstants.KEY_B, category)
        )
        undoKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping("key.easybuilding.undo", InputConstants.KEY_Z, category)
        )

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

        ClientTickEvents.START_CLIENT_TICK.register { client ->
            val player = client.player ?: return@register
            val mode = BuildState.mode
            if (firstPoint != null && !isScreenOpen(client) && mode != BuildMode.NORMAL && player.mainHandItem.item is BlockItem) {
                val hit = client.hitResult
                val aimingAtBlock = hit != null && hit.type == HitResult.Type.BLOCK
                if (!aimingAtBlock) {
                    while (client.options.keyUse.consumeClick()) {
                        handleInteraction(player, client, null)
                    }
                }
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val player = client.player

            while (menuKey.consumeClick()) {
                if (player != null) openScreen(ModeMenuScreen())
            }

            while (undoKey.consumeClick()) {
                if (player != null) {
                    if (isCtrlDown()) {
                        sendUndo()
                    } else if (firstPoint != null) {
                        cancelSelection()
                        chat(player, "Selection cancelled")
                    }
                }
            }

            if (firstPoint != null && client.options.keyAttack.consumeClick()) {
                if (player != null) {
                    cancelSelection()
                    chat(player, "Selection cancelled")
                }
            }

            updatePreview(client)
            drawMirrorPlane(client)
        }
    }

    private fun isCtrlDown(): Boolean {
        return runCatching {
            InputConstants.isKeyDown(341) ||
            InputConstants.isKeyDown(345) ||
            InputConstants.isKeyDown(343) ||
            InputConstants.isKeyDown(347)
        }.getOrDefault(false)
    }

    fun sendUndo() {
        ClientPlayNetworking.send(BuildPayload(BuildPayload.UNDO, 0, BlockPos.ZERO, BlockPos.ZERO, 0, BlockPos.ZERO))
    }

    fun cancelSelection() {
        firstPoint = null
        resetPreview()
    }

    private fun lockAxes(player: Player) {
        val look = player.lookAngle
        wallAxis = if (abs(look.z) >= abs(look.x)) 2 else 0
        spaceAxis = when {
            abs(look.y) > abs(look.x) && abs(look.y) > abs(look.z) -> 1
            abs(look.z) >= abs(look.x) -> 2
            else -> 0
        }
    }

    private fun handleInteraction(player: Player, client: Minecraft, hit: BlockHitResult?) {
        val mode = BuildState.mode
        if (mode == BuildMode.NORMAL) return

        val now = System.currentTimeMillis()
        if (now - lastClick < 250) return
        lastClick = now

        val first = firstPoint
        if (first == null) {
            if (hit != null && hit.type == HitResult.Type.BLOCK) {
                firstPoint = hit.blockPos.relative(hit.direction)
                lockAxes(player)
                chat(player, "First point set. Aim to second point and right-click (Z or Left-Click = cancel)")
            }
        } else {
            if (autoDirection) {
                lockAxes(player)
            }
            val target = cachedTarget ?: resolveTarget(player, client, first, mode)
            sendBuild(BuildPayload.BUILD, mode, first, target, player)
            firstPoint = null
            resetPreview()
        }
    }

    private fun axisValue(v: Vec3, axis: Int): Double = when (axis) {
        0 -> v.x
        1 -> v.y
        else -> v.z
    }

    private fun axisValue(p: BlockPos, axis: Int): Int = when (axis) {
        0 -> p.x
        1 -> p.y
        else -> p.z
    }

    private fun planeTarget(player: Player, first: BlockPos, axis: Int): BlockPos? {
        val eye = player.eyePosition
        val dir = player.lookAngle
        val d = axisValue(dir, axis)
        if (abs(d) < 1e-3) return null
        val t = (axisValue(first, axis) + 0.5 - axisValue(eye, axis)) / d
        if (t < 0.0 || t > MAX_AIM) return null
        val p = eye.add(dir.scale(t))
        return BlockPos(
            if (axis == 0) first.x else Mth.floor(p.x),
            if (axis == 1) first.y else Mth.floor(p.y),
            if (axis == 2) first.z else Mth.floor(p.z)
        )
    }

    private fun resolveTarget(player: Player, client: Minecraft, first: BlockPos, mode: BuildMode): BlockPos {
        val fallback = cachedTarget ?: first
        return when (mode) {
            BuildMode.FLOOR -> planeTarget(player, first, 1) ?: fallback
            BuildMode.WALL -> planeTarget(player, first, wallAxis) ?: fallback
            else -> {
                val hit = client.hitResult
                if (hit is BlockHitResult && hit.type == HitResult.Type.BLOCK) {
                    hit.blockPos.relative(hit.direction)
                } else {
                    planeTarget(player, first, spaceAxis) ?: fallback
                }
            }
        }
    }

    private fun buildScreenProbe(mc: Minecraft): (Minecraft) -> Any? {
        val guiField = runCatching { mc.javaClass.getField("gui") }.getOrNull()
        val gui = guiField?.let { runCatching { it.get(mc) }.getOrNull() }
        val guiMethod = gui?.javaClass?.methods?.firstOrNull { it.name == "screen" && it.parameterCount == 0 }
        if (guiField != null && guiMethod != null) {
            return { m -> runCatching { guiMethod.invoke(guiField.get(m)) }.getOrNull() }
        }

        val mcMethod = mc.javaClass.methods.firstOrNull { it.name == "screen" && it.parameterCount == 0 }
        if (mcMethod != null) {
            return { m -> runCatching { mcMethod.invoke(m) }.getOrNull() }
        }

        val mcField = runCatching { mc.javaClass.getField("screen") }.getOrNull()
        if (mcField != null) {
            return { m -> runCatching { mcField.get(m) }.getOrNull() }
        }

        return { _ -> null }
    }

    private fun isScreenOpen(client: Minecraft): Boolean {
        val probe = screenProbe ?: buildScreenProbe(client).also { screenProbe = it }
        return probe(client) != null
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
        rawPositions = null
        placeable = emptyList()
        affordable = 0
        tooBig = false
        cachedTarget = null
    }

    private fun withMirror(list: List<BlockPos>?): List<BlockPos>? {
        if (list == null) return null
        val m = BuildState.mirror
        val c = BuildState.mirrorCenter
        if (m == MirrorMode.OFF || c == null) return list
        return Mirror.apply(list, m, c)
    }

    private fun countItem(player: Player, item: BlockItem): Int {
        var total = 0
        for (i in 0 until 36) {
            val s = player.inventory.getItem(i)
            if (s.item == item) total += s.count
        }
        val off = player.offhandItem
        if (off.item == item) total += off.count
        return total
    }

    private fun refreshPlaceable(client: Minecraft, player: Player) {
        val raw = rawPositions
        val level = client.level
        if (raw == null || level == null) {
            placeable = emptyList()
            affordable = 0
            return
        }
        val seen = HashSet<BlockPos>()
        val ok = ArrayList<BlockPos>()
        val playerBox = player.boundingBox.inflate(-1e-4)

        for (p in raw) {
            if (!seen.add(p)) continue
            if (!level.hasChunkAt(p) || level.isOutsideBuildHeight(p)) continue
            if (!level.getBlockState(p).canBeReplaced()) continue
            if (playerBox.intersects(AABB(p).inflate(-1e-4))) continue
            ok.add(p)
        }
        placeable = ok
        val item = player.mainHandItem.item as? BlockItem
        affordable = if (player.isCreative || item == null) ok.size else minOf(ok.size, countItem(player, item))
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

        if (autoDirection) {
            lockAxes(player)
        }

        val target = resolveTarget(player, client, first, mode)
        tickCounter++

        val key = "${mode.ordinal}:$first:$target:${BuildState.mirror}:${BuildState.mirrorCenter}"
        if (key != lastKey) {
            lastKey = key
            cachedTarget = target
            val generated = ShapeGen.generate(mode, first, target)
            tooBig = generated == null
            rawPositions = withMirror(generated)
            refreshPlaceable(client, player)
            sendBuild(BuildPayload.PREVIEW, mode, first, target, player)
        } else if (tickCounter % REFRESH_TICKS == 0) {
            refreshPlaceable(client, player)
        }

        val shown = cachedTarget
        if (shown != null) drawPreview(first, shown)
    }

    private fun argb(alpha: Int, rgb: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)

    private fun blockBox(p: BlockPos): AABB =
        AABB(p.x.toDouble(), p.y.toDouble(), p.z.toDouble(), p.x + 1.0, p.y + 1.0, p.z + 1.0).inflate(0.002)

    private fun drawGroup(list: List<BlockPos>, fill: Int, line: Int) {
        if (list.isEmpty()) return
        val duration = 55
        if (list.size <= MAX_PER_BLOCK_PREVIEW) {
            for (p in list) {
                val box = blockBox(p)
                // setAlwaysOnTop يعزل الرسم تماماً عن تأثير ضوء الشمس وعمق البلوكات
                Gizmos.cuboid(box, GizmoStyle.fill(fill)).apply { persistForMillis(duration); setAlwaysOnTop() }
                Gizmos.cuboid(box, GizmoStyle.stroke(line)).apply { persistForMillis(duration); setAlwaysOnTop() }
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
            Gizmos.cuboid(box, GizmoStyle.fill(fill)).apply { persistForMillis(duration); setAlwaysOnTop() }
            Gizmos.cuboid(box, GizmoStyle.stroke(line)).apply { persistForMillis(duration); setAlwaysOnTop() }
        }
    }

    private fun drawPreview(first: BlockPos, target: BlockPos) {
        Gizmos.cuboid(blockBox(first), GizmoStyle.stroke(argb(140, 0x44FF55))).apply { persistForMillis(55); setAlwaysOnTop() }
        Gizmos.cuboid(blockBox(target), GizmoStyle.stroke(argb(140, 0xFFE24D))).apply { persistForMillis(55); setAlwaysOnTop() }

        if (tooBig) {
            val box = AABB(
                minOf(first.x, target.x).toDouble(), minOf(first.y, target.y).toDouble(), minOf(first.z, target.z).toDouble(),
                maxOf(first.x, target.x) + 1.0, maxOf(first.y, target.y) + 1.0, maxOf(first.z, target.z) + 1.0
            )
            Gizmos.cuboid(box, GizmoStyle.fill(argb(40, 0xFF3030))).apply { persistForMillis(55); setAlwaysOnTop() }
            Gizmos.cuboid(box, GizmoStyle.stroke(argb(120, 0xFF3030))).apply { persistForMillis(55); setAlwaysOnTop() }
            return
        }

        val white = placeable.subList(0, affordable.coerceIn(0, placeable.size))
        val missing = placeable.subList(white.size, placeable.size)

        // شفافية متزنة وثابتة في الليل والنهار بدون أي وميض
        val greenFill = argb(55, 0x38EF7D)
        val greenLine = argb(110, 0x38EF7D)

        drawGroup(white, greenFill, greenLine)
        drawGroup(missing, argb(45, 0xFF3030), argb(110, 0xFF3030))
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
        val line = GizmoStyle.stroke(argb(150, 0x00E5FF))

        if (m == MirrorMode.X || m == MirrorMode.BOTH) {
            val x = c.x + 0.5
            val box = AABB(x - 0.02, y0, c.z - half, x + 0.02, y1, c.z + half + 1.0)
            Gizmos.cuboid(box, fill).apply { persistForMillis(55); setAlwaysOnTop() }
            Gizmos.cuboid(box, line).apply { persistForMillis(55); setAlwaysOnTop() }
        }
        if (m == MirrorMode.Z || m == MirrorMode.BOTH) {
            val z = c.z + 0.5
            val box = AABB(c.x - half, y0, z - 0.02, c.x + half + 1.0, y1, z + 0.02)
            Gizmos.cuboid(box, fill).apply { persistForMillis(55); setAlwaysOnTop() }
            Gizmos.cuboid(box, line).apply { persistForMillis(55); setAlwaysOnTop() }
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
