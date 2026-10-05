package my.easybuilding.client

import com.mojang.blaze3d.platform.InputConstants
import kotlin.math.sin
import my.easybuilding.BuildMode
import my.easybuilding.BuildPayload
import my.easybuilding.BuildState
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
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

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

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (menuKey.consumeClick()) {
                if (client.player != null) openScreen(ModeMenuScreen())
            }
            updatePreview(client)
        }

        UseBlockCallback.EVENT.register { player, level, hand, hit ->
            val mode = BuildState.mode
            if (mode == BuildMode.NORMAL) firstPoint = null

            if (!level.isClientSide || hand != InteractionHand.MAIN_HAND || mode == BuildMode.NORMAL ||
                player.mainHandItem.item !is BlockItem
            ) {
                return@register InteractionResult.PASS
            }

            val now = System.currentTimeMillis()
            if (now - lastClick < 250) return@register InteractionResult.FAIL
            lastClick = now

            // sneak + right-click = cancel the selection
            if (player.isShiftKeyDown) {
                if (firstPoint != null) {
                    firstPoint = null
                    resetPreview()
                    chat(player, "Selection cancelled")
                    return@register InteractionResult.FAIL
                }
                return@register InteractionResult.PASS
            }

            val target = hit.blockPos.relative(hit.direction)
            val first = firstPoint
            if (first == null) {
                firstPoint = target
                chat(player, "First point set. Aim at the second point and right-click (sneak = cancel)")
            } else {
                ClientPlayNetworking.send(BuildPayload(BuildPayload.BUILD, mode.ordinal, first, target))
                firstPoint = null
                resetPreview()
            }
            InteractionResult.SUCCESS
        }
    }

    private fun chat(player: Player, text: String) {
        val msg = Component.literal("[Easy Building] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
            .append(Component.literal(text).withStyle(ChatFormatting.WHITE))
        player.sendSystemMessage(msg)
    }

    private fun resetPreview() {
        lastKey = null
        cachedPositions = null
        cachedTarget = null
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

        val hit = client.hitResult
        if (hit is BlockHitResult && hit.type == HitResult.Type.BLOCK) {
            val target = hit.blockPos.relative(hit.direction)
            val key = "${mode.ordinal}:$first:$target"
            if (key != lastKey) {
                lastKey = key
                cachedTarget = target
                cachedPositions = ShapeGen.generate(mode, first, target)
                ClientPlayNetworking.send(BuildPayload(BuildPayload.PREVIEW, mode.ordinal, first, target))
            }
        }
        if (cachedTarget != null) drawPreview(first, cachedTarget!!)
    }

    private fun argb(alpha: Int, rgb: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)

    private fun blockBox(p: BlockPos): AABB =
        AABB(p.x.toDouble(), p.y.toDouble(), p.z.toDouble(), p.x + 1.0, p.y + 1.0, p.z + 1.0).inflate(0.003)

    /** breathing translucent white over the blocks that will be built (red box if too big) */
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

    /** the screen-opening method moved between versions (Minecraft.setScreen or gui.setScreen), so find it by name */
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
