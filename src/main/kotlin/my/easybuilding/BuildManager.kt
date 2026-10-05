package my.easybuilding

import java.util.UUID
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState

object BuildManager {
    private const val MAX_RANGE = 128.0

    private data class LastBuild(
        val entries: List<Pair<BlockPos, BlockState>>,
        val item: Item,
        val count: Int
    )

    // يحفظ فقط آخر بناء لكل لاعب
    private val lastBuilds = HashMap<UUID, LastBuild>()

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

    private fun canPlaceAt(level: ServerLevel, pos: BlockPos): Boolean =
        level.hasChunkAt(pos) && !level.isOutsideBuildHeight(pos) && level.getBlockState(pos).canBeReplaced()

    private fun handle(player: ServerPlayer, p: BuildPayload) {
        if (p.action == BuildPayload.UNDO) {
            undo(player)
            return
        }

        val mode = BuildMode.entries.getOrNull(p.mode) ?: return
        if (mode == BuildMode.NORMAL) return
        val mirror = MirrorMode.entries.getOrNull(p.mirror) ?: MirrorMode.OFF
        val item = player.mainHandItem.item as? BlockItem

        if (tooFar(player, p.a) || tooFar(player, p.b) || (mirror != MirrorMode.OFF && tooFar(player, p.center))) {
            if (p.action == BuildPayload.BUILD) say(player, false, "Too far (max ${MAX_RANGE.toInt()} blocks)")
            return
        }

        var positions: List<BlockPos>? = ShapeGen.generate(mode, p.a, p.b)
        if (positions != null && mirror != MirrorMode.OFF) {
            val mirrored = Mirror.apply(positions, mirror, p.center)
            positions = if (mirrored.size > ShapeGen.MAX_BLOCKS * 4) null else mirrored
        }
        // حذف المواقع المكررة (تظهر مع الـ Mirror) حتى ما تنحسب كمتخطاة
        positions = positions?.distinct()

        if (p.action == BuildPayload.PREVIEW) preview(player, mode, item, positions) else build(player, item, positions, p.b)
    }

    private fun preview(player: ServerPlayer, mode: BuildMode, item: BlockItem?, positions: List<BlockPos>?) {
        if (positions == null) {
            hud(player, "Too big (max ${ShapeGen.MAX_BLOCKS} blocks)", ChatFormatting.RED)
            return
        }
        val level = player.level() as ServerLevel
        // نحسب فقط الأماكن اللي فعلاً راح ينبني عليها
        val need = positions.count { canPlaceAt(level, it) }
        if (need == 0) {
            hud(player, "${mode.label}: nothing to build here", ChatFormatting.RED)
            return
        }
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

        val history = ArrayList<Pair<BlockPos, BlockState>>()
        var placed = 0
        for (pos in positions) {
            if (placed >= budget) break
            if (!canPlaceAt(level, pos)) continue
            val oldState = level.getBlockState(pos)
            if (level.setBlock(pos, state, 3)) {
                history.add(pos to oldState)
                placed++
            }
        }

        if (!creative && placed > 0) consume(player, item, placed)
        if (placed > 0) {
            level.playSound(null, target, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f)
            lastBuilds[player.uuid] = LastBuild(history, item, if (creative) 0 else placed)
        }

        val skipped = positions.size - placed
        if (placed == 0) say(player, false, "Nothing was built (spots are occupied or no blocks)")
        else say(player, true, "Built $placed blocks" + if (skipped > 0) " ($skipped skipped)" else "")
    }

    private fun undo(player: ServerPlayer) {
        val last = lastBuilds.remove(player.uuid)
        if (last == null) {
            say(player, false, "Nothing to undo")
            return
        }
        val level = player.level() as ServerLevel
        var restored = 0
        for ((pos, oldState) in last.entries.asReversed()) {
            if (level.setBlock(pos, oldState, 3)) {
                restored++
            }
        }

        // إرجاع البلوكات إلى حقيبة اللاعب في طور Survival
        if (!player.isCreative && last.count > 0) {
            var remaining = last.count
            val maxStack = last.item.defaultMaxStackSize
            while (remaining > 0) {
                val take = minOf(remaining, maxStack)
                val stack = ItemStack(last.item, take)
                if (!player.inventory.add(stack)) {
                    // إذا الحقيبة ممتلئة تماماً، نرمي البلوكات عند أقدام اللاعب
                    val dropEntity = ItemEntity(level, player.x, player.y, player.z, stack)
                    level.addFreshEntity(dropEntity)
                }
                remaining -= take
            }
        }

        level.playSound(null, player.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f)
        say(player, true, "Undid last build ($restored blocks removed)")
    }
}
