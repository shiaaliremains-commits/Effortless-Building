package my.easybuilding

import net.minecraft.core.BlockPos

enum class BuildMode(val label: String) {
    NORMAL("Normal"),
    LINE("Line"),
    WALL("Wall"),
    FLOOR("Floor"),
    CUBE("Cube"),
    HOLLOW("Hollow Cube"),
    SPHERE("Sphere")
}

enum class MirrorMode(val label: String) {
    OFF("Off"),
    X("Mirror X"),
    Z("Mirror Z"),
    BOTH("Mirror X+Z")
}

object BuildState {
    @Volatile
    var mode: BuildMode = BuildMode.NORMAL

    @Volatile
    var mirror: MirrorMode = MirrorMode.OFF

    @Volatile
    var mirrorCenter: BlockPos? = null
}
