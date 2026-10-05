package my.easybuilding

import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

class BuildPayload(
    val action: Int,
    val mode: Int,
    val a: BlockPos,
    val b: BlockPos,
    val mirror: Int,
    val center: BlockPos
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        const val BUILD = 0
        const val PREVIEW = 1
        const val UNDO = 2

        val TYPE: CustomPacketPayload.Type<BuildPayload> =
            CustomPacketPayload.Type(Identifier.fromNamespaceAndPath("easybuilding", "build"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, BuildPayload> =
            StreamCodec.composite<RegistryFriendlyByteBuf, BuildPayload, Int, Int, BlockPos, BlockPos, Int, BlockPos>(
                ByteBufCodecs.INT, { p: BuildPayload -> p.action },
                ByteBufCodecs.INT, { p: BuildPayload -> p.mode },
                BlockPos.STREAM_CODEC, { p: BuildPayload -> p.a },
                BlockPos.STREAM_CODEC, { p: BuildPayload -> p.b },
                ByteBufCodecs.INT, { p: BuildPayload -> p.mirror },
                BlockPos.STREAM_CODEC, { p: BuildPayload -> p.center },
                { w: Int, x: Int, y: BlockPos, z: BlockPos, v: Int, u: BlockPos -> BuildPayload(w, x, y, z, v, u) }
            )
    }
}
