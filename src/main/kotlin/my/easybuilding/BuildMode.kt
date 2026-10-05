package my.easybuilding

enum class BuildMode(val label: String) {
    NORMAL("Normal"),
    LINE("Line"),
    WALL("Wall"),
    FLOOR("Floor"),
    CUBE("Cube"),
    HOLLOW("Hollow Cube"),
    SPHERE("Sphere")
}

object BuildState {
    @Volatile
    var mode: BuildMode = BuildMode.NORMAL
}
