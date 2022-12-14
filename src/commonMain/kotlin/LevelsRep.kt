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
    DMG("dmg"),
    FCSLR1("fc_slr1"),
    FCSLR2("fc_slr2"),
    HIRRCNT("hir_rcnt"),
    HISSHO2("hissho2"),
    LRPRO68K("lrpro68k"),
    MISC("misc"),
    MSX("msx"),
    MSX2("msx2"),
    MSX_CHP("msx_chp"),
    PC100("pc100"),
    PCENGINE("pcengine"),
    BUG("bug"),
//    CUSTOM("custom"),
    ;
    val dis get() = "${ordinal + 1}.${name.lowercase()}"
}

class LevelsRep(
    private val assets: AssetManager,
    val scope: CoroutineScope
) {

    val levels = mutableListOf <List<String>>()
    private val loadedLevels = mutableMapOf<Int, GameLevel>()
    companion object {
        val tilesByNames = Tile.values().associateBy { it.name.lowercase() }
    }

    suspend fun load(levelSet: LevelSet) {
        val json = assets.loadAsset("maps/${levelSet.path}.json")!!.toArray().decodeToString()
        val obj = Json.decodeFromString<JsonObject>(json)

        with(obj["levels"] as JsonObject) {
            val levelKeys = mutableSetOf(*keys.toTypedArray())
            levelKeys.remove("total")
            levelKeys.remove("name")
            levelKeys.remove("desc")
            levelKeys.remove("levelsDesc")
            levels.clear()

            for (levelName in levelKeys) {
                val rows = this[levelName] as JsonArray
                val map = rows.map { it.jsonPrimitive.content }
//                res.add(loadGameLevel(i, map, tileSet.nameIndex))
                levels.add(map)
            }
        }
    }

    fun getLevel(id: Int, tileAnims: AnimationFrames, generated: Boolean = true): GameLevel {

        val lid = id.mod(levels.size)
        return loadedLevels.getOrPut(lid) {
            if ( generated ) {
                val fromMap = levels[lid]

                generateGameLevel(
                    lid,
                    fromMap,
                    mapWidth = 24 + fromMap.first().length,
                    mapHeight = 12 + fromMap.size,
                    tilesAtlasIndex = tileAnims.sequence,
                    scope = scope
                )
            } else
                loadGameLevel(lid, levels[lid], tileAnims.sequence)
        }
    }
}

