import de.fabmax.kool.AssetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import me.az.ilode.GameLevel
import me.az.ilode.Tile
import me.az.ilode.generateGameLevel
import me.az.ilode.loadGameLevel

enum class LevelSet(val path: String) {
    CLASSIC("classic"),
    CHAMPIONSHIP("championship"),
    FANBOOK("fanbook"),
    PROFESSIONAL("professional"),
    REVENGE("revenge"),
    ;
    val dis get() = "${ordinal + 1}.${name.lowercase()}"
}

class LevelsRep(
    private val assets: AssetManager,
    val scope: CoroutineScope
) {

    val levels = mutableListOf <List<String>>()
    val loadedLevels = mutableMapOf<Int, GameLevel>()
    companion object {
        val tilesByNames = Tile.values().associateBy { it.name.lowercase() }
    }

    suspend fun load(levelSet: LevelSet) {
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

    fun getLevel(id: Int, tileSet: ImageAtlas, generated: Boolean = true): GameLevel {

        tileSet.frames.keys.forEach {
            if ( !tilesByNames.containsKey(it)) {
                //throw RuntimeException()
                println("$it not found in $tilesByNames")
            }
        }

        val lid = id.mod(levels.size)
        return loadedLevels.getOrPut(lid) {
            if ( generated ) {
                val fromMap = levels[lid]

                generateGameLevel(
                    lid,
                    fromMap,
                    mapWidth = 24 + fromMap.first().length,
                    mapHeight = 12 + fromMap.size,

                    tilesAtlasIndex = tileSet.nameIndex,
//                    holesIndex = holeAtlas.nameIndex,
//                    holesAnims = holeAnims,
                    scope = scope
                )
            } else
                loadGameLevel(lid, levels[lid], tileSet.nameIndex)
        }
    }
}

