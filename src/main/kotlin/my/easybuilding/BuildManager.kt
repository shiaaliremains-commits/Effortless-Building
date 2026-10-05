package my.easybuilding

import java.util.UUID
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

object BuildManager {
    private const val MAX_RANGE = 128.0

    private data class LastBuild(
        val entries: List<Pair<BlockPos, BlockState>>,
        val placedRecords: List<Pair<BlockPos, BlockState>>,
        val item: Item
    )

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

    // حساب البلوكات في اليد الثانية والشنطة الأساسية
    private fun count(player: ServerPlayer, item: Item): Int {
        var total = 0
        for (s in player.inventory.items) {
            if (s.item == item) total += s.count
        }
        for (s in player.inventory.offhand) {
            if (s.item == item) total += s.count
        }
        return total
    }

    // استهلاك البلوكات من اليد الثانية والشنطة
    private fun consume(player: ServerPlayer, item: Item, amount: Int) {
        var left = amount
        for (s in player.inventory.offhand) {
            if (left <= 0) break
            if (s.item == item) {
                val take = minOf(left, s.count)
                s.shrink(take)
                left -= take
            }
        }
        for (s in player.inventory.items) {
            if (left <= 0) break
            if (s.item == item) {
                val take = minOf(left, s.count)
                s.shrink(take)
                left -= take
            }
        }
    }

    // فحص البناء مع منع البناء داخل جسم اللاعب
    private fun canPlaceAt(level: ServerLevel, pos: BlockPos, player: ServerPlayer? = null): Boolean {
        if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos) || !level.getBlockState(pos).canBeReplaced()) {
            return false
        }
        if (player != null) {
            val blockBox = AABB(pos).deflate(1e-4)
            if (player.boundingBox.intersects(blockBox)) return false
        }
        return true
    }

    // تحديد اتجاه البلوكة الذكي (درج، خشب، سلاب، إلخ)
    private fun getPlacementState(player: ServerPlayer, item: BlockItem, pos: BlockPos): BlockState {
        return runCatching {
            val look = player.lookAngle
            val dir = Direction.getNearest(look.x, look.y, look.z).opposite
            val hit = BlockHitResult(Vec3.atCenterOf(pos), dir, pos, false)
            val ctx = BlockPlaceContext(player, InteractionHand.MAIN_HAND, player.mainHandItem, hit)
            item.getPlacementState(ctx)
        }.getOrNull() ?: item.block.defaultBlockState()
    }

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
        positions = positions?.distinct()

        if (p.action == BuildPayload.PREVIEW) preview(player, mode, item, positions) else build(player, item, positions, p.b)
    }

    private fun preview(player: ServerPlayer, mode: BuildMode, item: BlockItem?, positions: List<BlockPos>?) {
        if (positions == null) {
            hud(player, "Too big (max ${ShapeGen.MAX_BLOCKS} blocks)", ChatFormatting.RED)
            return
        }
        val level = player.level() as ServerLevel
        val need = positions.count { canPlaceAt(level, it, player) }
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
        val creative = player.isCreative
        val budget = if (creative) Int.MAX_VALUE else count(player, item)
        if (budget <= 0) {
            say(player, false, "No blocks in your inventory")
            return
        }

        val history = ArrayList<Pair<BlockPos, BlockState>>()
        val placedRecords = ArrayList<Pair<BlockPos, BlockState>>()
        var placed = 0
        for (pos in positions) {
            if (placed >= budget) break
            if (!canPlaceAt(level, pos, player)) continue
            val oldState = level.getBlockState(pos)
            val state = getPlacementState(player, item, pos)
            if (level.setBlock(pos, state, 3)) {
                history.add(pos to oldState)
                placedRecords.add(pos to state)
                placed++
            }
        }

        if (!creative && placed > 0) consume(player, item, placed)
        if (placed > 0) {
            level.playSound(null, target, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f)
            lastBuilds[player.uuid] = LastBuild(history, placedRecords, item)
        }

        val skipped = positions.size - placed
        if (placed == 0) say(player, false, "Nothing was built (spots are occupied, player inside, or no blocks)")
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
        val placedMap = last.placedRecords.toMap()

        for ((pos, oldState) in last.entries.asReversed()) {
            val currentState = level.getBlockState(pos)
            val expectedPlacedState = placedMap[pos]
            // قفل ثغرة التدبيل: التراجع يتم فقط إذا كانت البلوكة لا تزال موجودة ولم تُكسر
            if (currentState == expectedPlacedState) {
                if (level.setBlock(pos, oldState, 3)) {
                    restored++
                }
            }
        }

        if (!player.isCreative && restored > 0) {
            var remaining = restored
            val maxStack = last.item.defaultMaxStackSize
            while (remaining > 0) {
                val take = minOf(remaining, maxStack)
                val stack = ItemStack(last.item, take)
                if (!player.inventory.add(stack)) {
                    val dropEntity = ItemEntity(level, player.x, player.y, player.z, stack)
                    level.addFreshEntity(dropEntity)
                }
                remaining -= take
            }
        }

        if (restored > 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f)
            say(player, true, "Undid last build ($restored blocks removed)")
        } else {
            say(player, false, "Nothing to undo (blocks were already broken or changed)")
        }
    }
}
