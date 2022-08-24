package me.az.ilode

import Tile
import TileLogicType
import ViewCell
import de.fabmax.kool.math.MutableVec2i
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
                        runner.x = x
                        runner.y = y
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

    }
}

class GameLevel(
    val levelId: Int,
    val width: Int,
    val height: Int,
    private val primaryTileSet: Map<String, Int> // index
) {

    enum class Status {
        LEVEL_STARTUP,
        LEVEL_PAUSED,
        LEVEL_PLAYING
    }
    // store view info
    val buf = createUint8Buffer(width * height)
    var runner = MutableVec2i()
    val guards = mutableListOf<Vec2i>()
    var gold = 0
    var status = Status.LEVEL_STARTUP

    // store logic info
    val act = Array(width) { Array(height) { TileLogicType.EMPTY } }
    val base = Array(width) { Array(height) { TileLogicType.EMPTY } }
    val guard = Array(width) { Array(height) { false } }
    var dirty = false


    // 0 - 6 bits
    // 7 bit - for hole as frame tile set

    operator fun get(x: Int, y: Int): ViewCell? = if ( isValid(x, y) ) ViewCell.unpack(buf[y * width + x]) else null
    operator fun set(x: Int, y: Int, v: ViewCell)  { if ( isValid(x, y) ) buf[y * width + x] = v.pack; dirty = true }

    operator fun get(at: Vec2i) = get(at.x, at.y)
    operator fun set(at: Vec2i, v: ViewCell) = set(at.x, at.y, v)
    operator fun set(at: Vec2i, t: Tile) {
        if (isValid(at)) {
            set(at.x, at.y, ViewCell(get(at.x, at.y)!!.hole, primaryTileSet[t.frame]!!))
        }
    }

    fun getAct(at: Vec2i) = act[at.x][at.y]
    fun getBase(at: Vec2i) = base[at.x][at.y]

    private fun isValid(x: Int, y: Int) = x >= 0 && x < width && y >= 0 && y < height
    private fun isValid(at: Vec2i) = isValid(at.x, at.y)

    fun updateTileMap(): TextureData2d {
        return TextureData2d(buf, width, height, TexFormat.R)
    }

    fun isBarrier(x: Int, y: Int) = !isValid(x, y) ||
        act[x][y] == TileLogicType.BLOCK || act[x][y] == TileLogicType.SOLID ||
        act[x][y] == TileLogicType.TRAP
    fun isBarrier(at: Vec2i) = isBarrier(at.x, at.y)

    fun isLadder(x: Int, y: Int, hidden: Boolean) = isValid(x, y) && (
        act[x][y] == TileLogicType.LADDR || (base[x][y] == TileLogicType.HLADR && hidden)
    )
    fun isLadder(at: Vec2i, hidden: Boolean) = isLadder(at.x, at.y, hidden)

    fun isFloor(x: Int, y: Int, useBase: Boolean = false, useGuard: Boolean = true): Boolean {
        val check = if ( useBase ) { base } else { act }
        return !isValid(x, y) || check[x][y] == TileLogicType.BLOCK || check[x][y] == TileLogicType.SOLID ||
                check[x][y] == TileLogicType.LADDR || (useGuard && guard[x][y])
    }
    fun isFloor(at: Vec2i, useBase: Boolean = false, useGuard: Boolean = true) = isFloor(at.x, at.y, useBase, useGuard)

    fun isBar(x: Int, y: Int) = isValid(x, y) && base[x][y] == TileLogicType.BAR
    fun isBar(at: Vec2i) = isBar(at.x, at.y)

    fun isGold(x: Int, y: Int) = isValid(x, y) && base[x][y] == TileLogicType.GOLD

    fun isEmpty(x: Int, y: Int) = isValid(x, y) && base[x][y] == TileLogicType.EMPTY
    fun isEmpty(at: Vec2i) = isEmpty(at.x, at.y)

    fun hasGuard(x: Int, y: Int) = isValid(x, y) && guard[x][y]
    fun hasGuard(at: Vec2i) = hasGuard(at.x, at.y)

}