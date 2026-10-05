package my.easybuilding

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item

/** Server side: checks the request, places the blocks and takes them from the inventory. */
object BuildManager {
    private const val MAX_RANGE = 128.0

    fun init() {
        PayloadTypeRegistry.serverboundPlay().register(BuildPayload.TYPE, BuildPayload.CODEC)
        ServerPlayNetworking.registerGlobalReceiver(BuildPayload.TYPE) { payload, context ->
            handle(context.player(), payload)
        }
    }

    private fun say(player: ServerPlayer, ok: Boolean, text: String) {
        val color = if (ok) ChatFormatting.GREEN else ChatFormatting.RED
        val symbol = if (ok) "\u2714 " else "\u2716 "
        val msg = Component.literal(symbol).withStyle(color, ChatFormatting.BOLD)
            .append(Component.literal(text).withStyle(ChatFormatting.WHITE))
        player.sendSystemMessage(msg, false)
    }

    private fun hud(player: ServerPlayer, text: String, color: ChatFormatting) {
        player.sendSystemMessage(Component.literal(text).withStyle(color, ChatFormatting.BOLD), true)
    }

    private fun tooFar(player: ServerPlayer, pos: BlockPos): Boolean =
        player.distanceToSqr(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5) > MAX_RANGE * MAX_RANGE

    private fun count(player: ServerPlayer, item: Item): Int {
        var total = 0
        for (i in 0 until 36) {
            val s = player.inventory.getItem(i)
            if (s.item == item) total += s.count
        }
        return total
    }

    private fun consume(player: ServerPlayer, item: Item, amount: Int) {
        var left = amount
        for (i in 0 until 36) {
            if (left <= 0) break
            val s = player.inventory.getItem(i)
            if (s.item == item) {
                val take = minOf(left, s.count)
                s.shrink(take)
                left -= take
            }
        }
    }

    private fun handle(player: ServerPlayer, p: BuildPayload) {
        val mode = BuildMode.entries.getOrNull(p.mode) ?: return
        if (mode == BuildMode.NORMAL) return
        val item = player.mainHandItem.item as? BlockItem
        if (tooFar(player, p.a) || tooFar(player, p.b)) {
            if (p.action == BuildPayload.BUILD) say(player, false, "Too far (max ${MAX_RANGE.toInt()} blocks)")
            return
        }
        val positions = ShapeGen.generate(mode, p.a, p.b)
        if (p.action == BuildPayload.PREVIEW) preview(player, mode, item, positions) else build(player, item, positions, p.b)
    }

    private fun preview(player: ServerPlayer, mode: BuildMode, item: BlockItem?, positions: List<BlockPos>?) {
        if (positions == null) {
            hud(player, "Too big (max ${ShapeGen.MAX_BLOCKS} blocks)", ChatFormatting.RED)
            return
        }
        val need = positions.size
        if (item == null || player.isCreative) {
            hud(player, "${mode.label}: $need blocks", ChatFormatting.AQUA)
            return
        }
        val have = count(player, item)
        val color = if (have >= need) ChatFormatting.AQUA else ChatFormatting.RED
        hud(player, "${mode.label}: $need blocks | you have $have", color)
    }

    private fun build(player: ServerPlayer, item: BlockItem?, positions: List<BlockPos>?, target: BlockPos) {
        if (!player.mayBuild()) {
            say(player, false, "You can't build here")
            return
        }
        if (item == null) {
            say(player, false, "Hold a block to build")
            return
        }
        if (positions == null) {
            say(player, false, "Too big (max ${ShapeGen.MAX_BLOCKS} blocks)")
            return
        }
        val level = player.level() as ServerLevel
        val state = item.block.defaultBlockState()
        val creative = player.isCreative
        val budget = if (creative) Int.MAX_VALUE else count(player, item)
        if (budget <= 0) {
            say(player, false, "No blocks in your inventory")
            return
        }

        var placed = 0
        for (pos in positions) {
            if (placed >= budget) break
            if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)) continue
            if (!level.getBlockState(pos).canBeReplaced()) continue
            if (level.setBlock(pos, state, 3)) placed++
        }

        if (!creative && placed > 0) consume(player, item, placed)
        if (placed > 0) level.playSound(null, target, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f)

        val skipped = positions.size - placed
        if (placed == 0) say(player, false, "Nothing was built (spots are occupied or no blocks)")
        else say(player, true, "Built $placed blocks" + if (skipped > 0) " ($skipped skipped)" else "")
    }
}
