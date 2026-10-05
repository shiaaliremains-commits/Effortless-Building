package my.easybuilding

import net.minecraft.core.BlockPos

object BuildState {
    var mode: BuildMode = BuildMode.NORMAL
    var mirror: MirrorMode = MirrorMode.OFF
    var mirrorCenter: BlockPos? = null
    var autoDirection: Boolean = true // الميزة مفعلة افتراضياً
}
