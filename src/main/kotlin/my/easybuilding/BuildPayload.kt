package my.easybuilding

import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/** client -> server: build the shape (BUILD) or ask for the block count of the preview (PREVIEW) */
class BuildPayload(val action: Int, val mode: Int, val a: BlockPos, val b: BlockPos) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        const val BUILD = 0
        const val PREVIEW = 1

        val TYPE: CustomPacketPayload.Type<BuildPayload> =
            CustomPacketPayload.Type(Identifier.fromNamespaceAndPath("easybuilding", "build"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, BuildPayload> =
            StreamCodec.composite<RegistryFriendlyByteBuf, BuildPayload, Int, Int, BlockPos, BlockPos>(
                ByteBufCodecs.INT, { p: BuildPayload -> p.action },
                ByteBufCodecs.INT, { p: BuildPayload -> p.mode },
                BlockPos.STREAM_CODEC, { p: BuildPayload -> p.a },
                BlockPos.STREAM_CODEC, { p: BuildPayload -> p.b },
                { x: Int, y: Int, z: BlockPos, w: BlockPos -> BuildPayload(x, y, z, w) }
            )
    }
}
