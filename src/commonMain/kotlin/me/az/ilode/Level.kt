package me.az.ilode

import Tile
import TileLogicType
import ViewCell
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.pipeline.TexFormat
import de.fabmax.kool.pipeline.TextureData2d
import de.fabmax.kool.util.createUint8Buffer
import kotlin.properties.Delegates

fun loadGameLevel(levelId: Int, map: List<String>, tilesAtlasIndex: Map<String, Int>): GameLevel {
    return GameLevel(levelId, map.first().length, map.size, tilesAtlasIndex).apply {
        // for load

        map.forEachIndexed { y, row ->
            row.forEachIndexed { x, cell ->
                var guard = false

                val tile = Tile.values().first { it.char == cell }


                when(tile) {
                    Tile.PLAYER -> {
                        playerX = x
                        playerY = y
                    }
                    Tile.GUARD -> {
                        guard = true
                        guards.add(Vec2i(x, y))
                    }
                    Tile.GOLD -> {
                        gold ++
                    }
                    else -> Unit
                }

                this.act[x][y] = tile.act
                this.base[x][y] = tile.base
                this.guard[x][y] = guard
                this[x, y] = ViewCell(false, tilesAtlasIndex[tile.frame.ifEmpty { Tile.EMPTY.frame }]!!)
            }
        }

        buf.flip()
    }
}

class GameLevel(
    private val levelId: Int,
    private val width: Int,
    private val height: Int,
    private val primaryTileSet: Map<String, Int> // index
) {

    enum class Status {
        LEVEL_STARTUP,
        LEVEL_PAUSED,
        LEVEL_PLAYING
    }
    // store view info
    val buf = createUint8Buffer(width * height)
    var playerX by Delegates.notNull<Int>()
    var playerY by Delegates.notNull<Int>()
    val guards = mutableListOf<Vec2i>()
    var gold = 0
    var status = Status.LEVEL_STARTUP

    // store logic info
    val act = Array(width) { Array(height) { TileLogicType.EMPTY } }
    val base = Array(width) { Array(height) { TileLogicType.EMPTY } }
    val guard = Array(width) { Array(height) { false } }

    // 0 - 6 bits
    // 7 bit - for hole as frame tile set

//    private fun UInt.packBaseTile(t: TileType) = (this and 0xfff0u) or (t.ordinal.toUInt() and 0x000fu)
//    private fun UInt.packActTile(t: TileType) = (this and 0xff0fu) or ((t.ordinal.toUInt() and 0x000fu) shl 4)

//    private fun UInt.unpackBaseTile() = TileType.values()[(this and 0x000fu).toInt()]
//    private fun UInt.unpackActTile() = TileType.values()[((this shr 4) and 0x000fu).toInt()]

//    private fun UInt.unpackGuard() = getBit(5)

//    private fun UInt.packGuard(v: Boolean) = withBit(5, v)

    operator fun get(x: Int, y: Int): ViewCell? = if ( isValid(x, y) ) ViewCell.unpack(buf[y * width + x]) else null
    operator fun set(x: Int, y: Int, v: ViewCell)  { if ( isValid(x, y) ) buf[y * width + x] = v.pack }
    private fun isValid(at: Vec2i) = isValid(at.x, at.y)
    private fun isValid(x: Int, y: Int) = x >= 0 && x < width && y >= 0 && y < height

    fun updateTileMap(): TextureData2d {
        return TextureData2d(buf, width, height, TexFormat.R)
    }

    fun isBarrier(x: Int, y: Int) = !isValid(x, y) ||
        act[x][y] == TileLogicType.BLOCK || act[x][y] == TileLogicType.SOLID ||
        act[x][y] == TileLogicType.TRAP

    fun isLadder(x: Int, y: Int, hidden: Boolean) = isValid(x, y) && (
        act[x][y] == TileLogicType.LADDR || (base[x][y] == TileLogicType.HLADR && hidden)
    )

    fun isFloor(x: Int, y: Int, useBase: Boolean = false, useGuard: Boolean = true): Boolean {
        val check = if ( useBase ) { base } else { act }
        return !isValid(x, y) || check[x][y] == TileLogicType.BLOCK || check[x][y] == TileLogicType.SOLID ||
                check[x][y] == TileLogicType.LADDR || (useGuard && guard[x][y])
    }

    fun isBar(x: Int, y: Int) = isValid(x, y) && base[x][y] == TileLogicType.BAR
    fun isGold(x: Int, y: Int) = isValid(x, y) && base[x][y] == TileLogicType.GOLD
    fun isEmpty(x: Int, y: Int) = isValid(x, y) && base[x][y] == TileLogicType.EMPTY
    fun hasGuard(x: Int, y: Int) = isValid(x, y) && guard[x][y]

}