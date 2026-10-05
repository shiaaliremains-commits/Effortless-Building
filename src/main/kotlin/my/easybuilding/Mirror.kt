package my.easybuilding

import net.minecraft.core.BlockPos

/** Mirrors a list of positions across the plane(s) through the middle of the center block. */
object Mirror {
    fun apply(list: List<BlockPos>, mode: MirrorMode, center: BlockPos): List<BlockPos> {
        if (mode == MirrorMode.OFF) return list
        val out = LinkedHashSet<BlockPos>(list)
        for (p in list) {
            val mx = 2 * center.x - p.x
            val mz = 2 * center.z - p.z
            when (mode) {
                MirrorMode.X -> out.add(BlockPos(mx, p.y, p.z))
                MirrorMode.Z -> out.add(BlockPos(p.x, p.y, mz))
                MirrorMode.BOTH -> {
                    out.add(BlockPos(mx, p.y, p.z))
                    out.add(BlockPos(p.x, p.y, mz))
                    out.add(BlockPos(mx, p.y, mz))
                }
                MirrorMode.OFF -> {}
            }
        }
        return ArrayList(out)
    }
}
