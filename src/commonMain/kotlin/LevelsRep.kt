import de.fabmax.kool.AssetManager
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import me.az.ilode.GameLevel
import me.az.ilode.loadGameLevel

enum class LevelSet(val path: String) {
    CLASSIC("classic")
}

enum class TileLogicType() {
    EMPTY,
    BLOCK,
    SOLID,
    LADDR,
    BAR,
    TRAP,
    HLADR,
    GOLD,
}

/// named by frame names
enum class Tile(val char: Char, val base: TileLogicType, val act: TileLogicType, val frameName: String? = null) {
    EMPTY(' ', TileLogicType.EMPTY, TileLogicType.EMPTY ),
    BRICK('#', TileLogicType.BLOCK, TileLogicType.BLOCK ), // Normal Brick
    SOLID('@', TileLogicType.SOLID, TileLogicType.SOLID ), // Solid Brick
    LADDER('H', TileLogicType.LADDR, TileLogicType.LADDR ), // Ladder
    ROPE('-', TileLogicType.BAR, TileLogicType.BAR ), // Line of rope
    TRAP('X', TileLogicType.TRAP, TileLogicType.EMPTY, BRICK.name.lowercase() ), // False brick
    HLADDER('S', TileLogicType.HLADR, TileLogicType.EMPTY, LADDER.name.lowercase() ), //Ladder appears at end of level
    GOLD('$', TileLogicType.GOLD, TileLogicType.EMPTY ),
    GUARD('0', TileLogicType.EMPTY, TileLogicType.EMPTY, "" ),
    PLAYER('&', TileLogicType.EMPTY, TileLogicType.EMPTY, "" );
    val frame: String get() = frameName ?: name.lowercase()
}

data class ViewCell (
    var hole: Boolean, // maybe several atlases, TBD
    var frameNum: Int
) {
    val pack: Byte get() {
        var b: UInt = 0u
        b = b.packHole(hole)
        b = b.packFrameNum(frameNum)
        return b.toByte()
    }

    companion object {
        fun unpack(b: Byte) = with(b.toUInt()) {
            ViewCell(
                unpackHole(),
                unpackFrameNum()
            )
        }

        private fun UInt.getBit(position: Int): Boolean = (this shr position) and 1u > 0u
        private fun UInt.withBit(position: Int, bit: Boolean): UInt {
            return if ( bit ) {
                this or (1u shl position)
            } else {
                this and (1u shl position).inv()
            }
        }
        private fun UInt.packHole(v: Boolean) = withBit(7, v)
        private fun UInt.unpackHole() = getBit(7)
        private fun UInt.packFrameNum(f: Int) = (this and 0xff80u) or ( f.toUInt() and 0x007fu )
        private fun UInt.unpackFrameNum() = (this and 0xff7fu).toInt()
    }
}

class LevelsRep(val assets: AssetManager, val tileSet: ImageAtlas) {

    val levels = mutableListOf <List<String>>()
    val loadedLevels = mutableMapOf<Int, GameLevel>()
    companion object {
        val tilesByNames = Tile.values().associateBy { it.name.lowercase() }
    }

    val tilesIndex by lazy {
        Tile.values().filter { tileSet.nameIndex.containsKey(it.name.lowercase()) }
            .associateWith { tileSet.nameIndex[it.name.lowercase()]!! }
    }

    suspend fun load(levelSet: LevelSet) {
        tileSet.frames.keys.forEach {
            if ( !tilesByNames.containsKey(it)) {
                //throw RuntimeException()
                println("$it not found in $tilesByNames")
            }
        }

        val json = assets.loadAsset("maps/${levelSet.path}.json")!!.toArray().decodeToString()
        val obj = Json.decodeFromString<JsonObject>(json)

        with(obj["levels"] as JsonObject) {
            val total = this["total"] as JsonPrimitive

            levels.clear()

            for (i in 1 .. total.int) {
                val rows = this["level-" + i.toString().padStart(3, '0')] as JsonArray
                val map = rows.map { it.jsonPrimitive.content }
//                res.add(loadGameLevel(i, map, tileSet.nameIndex))
                levels.add(map)
            }
        }
    }

    fun getLevel(id: Int): GameLevel {
        return loadedLevels.getOrPut(id) { loadGameLevel(id, levels[id], tileSet.nameIndex) }
    }
}

