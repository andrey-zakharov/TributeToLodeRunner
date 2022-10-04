import de.fabmax.kool.AssetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import me.az.ilode.GameLevel
import me.az.ilode.Tile
import me.az.ilode.generateGameLevel
import me.az.ilode.loadGameLevel

enum class LevelSet(val path: String) {
    CLASSIC("classic")
}

class LevelsRep(
    private val assets: AssetManager,
    private val tileSet: ImageAtlas,
    val holeAtlas: ImageAtlas,
    val holeAnims: AnimationFrames,
    val scope: CoroutineScope
) {

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

    fun getLevel(id: Int, generated: Boolean = true): GameLevel {
        return loadedLevels.getOrPut(id) {
            if ( generated )
                generateGameLevel(id, levels[id], tileSet.nameIndex, holeAtlas.nameIndex, holeAnims, scope)
            else
                loadGameLevel(id, levels[id], tileSet.nameIndex, holeAtlas.nameIndex, holeAnims)
        }
    }
}

