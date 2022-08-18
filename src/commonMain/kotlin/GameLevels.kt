import de.fabmax.kool.AssetManager
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.pipeline.TexFormat
import de.fabmax.kool.pipeline.TextureData2d
import de.fabmax.kool.util.createUint8Buffer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import kotlin.properties.Delegates

enum class LevelSet(val path: String) {
    CLASSIC("classic")
}

enum class TileType() {
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
enum class Tile(val char: Char, val base: TileType, val act: TileType, val frameName: String? = null) {
    EMPTY(' ', TileType.EMPTY, TileType.EMPTY, "" ),
    BRICK('#', TileType.BLOCK, TileType.BLOCK ), // Normal Brick
    SOLID('@', TileType.SOLID, TileType.SOLID ), // Solid Brick
    LADDER('H', TileType.LADDR, TileType.LADDR ), // Ladder
    ROPE('-', TileType.BAR, TileType.BAR ), // Line of rope
    TRAP('X', TileType.TRAP, TileType.EMPTY, BRICK.name.lowercase() ), // False brick
    HLADDER('S', TileType.HLADR, TileType.EMPTY, LADDER.name.lowercase() ), //Ladder appears at end of level
    GOLD('$', TileType.GOLD, TileType.EMPTY ),
    GUARD('0', TileType.EMPTY, TileType.EMPTY, "" ),
    PLAYER('&', TileType.EMPTY, TileType.EMPTY, "" )
}

class GameLevels(val assets: AssetManager, val tileSet: ImageAtlas) {

    lateinit var levels: Array<GameLevel>
    val tilesByChars: Map<Char, Tile> = Tile.values().associateBy { it.char }
    val tilesByNames = Tile.values().associateBy { it.name.lowercase() }
    val tilesIndex by lazy { Tile.values().filter { tileSet.nameIndex.containsKey(it.name.lowercase()) }
        .associate { it.char to tileSet.nameIndex[it.name.lowercase()]!! } }

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

            val res = mutableListOf<GameLevel>()

            for (i in 1 .. total.int) {
                val rows = this["level-" + i.toString().padStart(3, '0')] as JsonArray
                val map = rows.map { it.jsonPrimitive.content }
                res.add(GameLevel(map, tilesIndex))
            }

            levels = res.toTypedArray()
        }
    }
}

class GameLevel(private val map: List<String>, val tileSet: Map<Char, Int>) {
    private val width = map.first().length
    private val height = map.size
    var playerX by Delegates.notNull<Int>()
    var playerY by Delegates.notNull<Int>()
    val guards = mutableListOf<Vec2i>()

    fun createTileMap(): TextureData2d {
        val buf = createUint8Buffer(width * height)
        println(map)
        println(tileSet)
        map.forEachIndexed { y, row ->
            row.forEachIndexed { x, cell ->
                val drawChar = when(cell) {
                    Tile.PLAYER.char -> {
                        playerX = x
                        playerY = y
                        Tile.EMPTY.char
                    }
                    Tile.GUARD.char -> {
                        guards.add(Vec2i(x, y))
                        Tile.EMPTY.char
                    }
                    else -> cell
                }

                buf.put(tileSet[drawChar]!!.toByte())
            }
        }

        buf.flip()
        return TextureData2d(buf, width, height, TexFormat.R)
    }

}