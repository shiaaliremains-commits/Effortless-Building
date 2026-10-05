package my.easybuilding

import kotlin.math.abs
import kotlin.math.sqrt
import net.minecraft.core.BlockPos

/** Creates the block positions of each shape. Returns null when the shape is too big. */
object ShapeGen {
    const val MAX_BLOCKS = 2048
    private const val MAX_SCAN = 300_000L

    fun generate(mode: BuildMode, a: BlockPos, b: BlockPos): List<BlockPos>? = when (mode) {
        BuildMode.NORMAL -> listOf(b)
        BuildMode.LINE -> line(a, b)
        BuildMode.WALL -> wall(a, b)
        BuildMode.FLOOR -> floor(a, b)
        BuildMode.CUBE -> box(a, b, false)
        BuildMode.HOLLOW -> box(a, b, true)
        BuildMode.SPHERE -> sphere(a, b)
    }

    private fun line(a: BlockPos, b: BlockPos): List<BlockPos>? {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val dz = b.z - a.z
        val n = maxOf(abs(dx), abs(dy), abs(dz))
        if (n + 1 > MAX_BLOCKS) return null
        if (n == 0) return listOf(a)
        val out = ArrayList<BlockPos>(n + 1)
        for (i in 0..n) {
            val t = i.toDouble() / n
            out.add(
                BlockPos(
                    Math.round(a.x + dx * t).toInt(),
                    Math.round(a.y + dy * t).toInt(),
                    Math.round(a.z + dz * t).toInt()
                )
            )
        }
        return out
    }

    private fun wall(a: BlockPos, b: BlockPos): List<BlockPos>? {
        val minX = minOf(a.x, b.x)
        val maxX = maxOf(a.x, b.x)
        val minY = minOf(a.y, b.y)
        val maxY = maxOf(a.y, b.y)
        val minZ = minOf(a.z, b.z)
        val maxZ = maxOf(a.z, b.z)
        val alongX = abs(b.x - a.x) >= abs(b.z - a.z)
        val w = if (alongX) maxX - minX + 1 else maxZ - minZ + 1
        val h = maxY - minY + 1
        if (w.toLong() * h > MAX_BLOCKS) return null
        val out = ArrayList<BlockPos>()
        for (y in minY..maxY) {
            for (i in 0 until w) {
                out.add(if (alongX) BlockPos(minX + i, y, a.z) else BlockPos(a.x, y, minZ + i))
            }
        }
        return out
    }

    private fun floor(a: BlockPos, b: BlockPos): List<BlockPos>? {
        val minX = minOf(a.x, b.x)
        val maxX = maxOf(a.x, b.x)
        val minZ = minOf(a.z, b.z)
        val maxZ = maxOf(a.z, b.z)
        if ((maxX - minX + 1).toLong() * (maxZ - minZ + 1) > MAX_BLOCKS) return null
        val out = ArrayList<BlockPos>()
        for (x in minX..maxX) for (z in minZ..maxZ) out.add(BlockPos(x, a.y, z))
        return out
    }

    private fun box(a: BlockPos, b: BlockPos, hollow: Boolean): List<BlockPos>? {
        val minX = minOf(a.x, b.x)
        val maxX = maxOf(a.x, b.x)
        val minY = minOf(a.y, b.y)
        val maxY = maxOf(a.y, b.y)
        val minZ = minOf(a.z, b.z)
        val maxZ = maxOf(a.z, b.z)
        val volume = (maxX - minX + 1).toLong() * (maxY - minY + 1) * (maxZ - minZ + 1)
        if (!hollow && volume > MAX_BLOCKS) return null
        if (hollow && volume > MAX_SCAN) return null
        val out = ArrayList<BlockPos>()
        for (x in minX..maxX) for (y in minY..maxY) for (z in minZ..maxZ) {
            if (hollow && x != minX && x != maxX && y != minY && y != maxY && z != minZ && z != maxZ) continue
            out.add(BlockPos(x, y, z))
            if (out.size > MAX_BLOCKS) return null
        }
        return out
    }

    private fun sphere(c: BlockPos, e: BlockPos): List<BlockPos>? {
        val dx = e.x - c.x
        val dy = e.y - c.y
        val dz = e.z - c.z
        val r = sqrt((dx * dx + dy * dy + dz * dz).toDouble())
        val ri = Math.round(r).toInt().coerceAtLeast(1)
        val side = 2L * ri + 1
        if (side * side * side > MAX_SCAN) return null
        val out = ArrayList<BlockPos>()
        for (x in -ri..ri) for (y in -ri..ri) for (z in -ri..ri) {
            val d = sqrt((x * x + y * y + z * z).toDouble())
            if (abs(d - ri) < 0.5) {
                out.add(BlockPos(c.x + x, c.y + y, c.z + z))
                if (out.size > MAX_BLOCKS) return null
            }
        }
        return out
    }
}
