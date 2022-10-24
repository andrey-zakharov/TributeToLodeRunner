import de.fabmax.kool.AssetManager
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.math.Vec4i
import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.scene.geometry.RectProps
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

enum class TileSet(val path: String, val tileWidth: Int = 20, val tileHeight: Int = 22) {
    SPRITES_APPLE2("ap2"),
    SPRITES_COMMODORE64("c64"),
    SPRITES_IBM("ibm"),
    SPRITES_ATARI8BIT("a8b"),
    SPRITES_ZXSPECTRUM("zxs"),
    SPRITES_NES("nes"),
    ;
    val dis get() = name.removePrefix("SPRITES_")
}
data class GapsSpec(
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
    val left: Int = 0,
)
data class ImageAtlasSpec(
    val tileset: TileSet = TileSet.SPRITES_APPLE2,
    val tileWidth: Int = tileset.tileWidth,
    val tileHeight: Int = tileset.tileHeight,
    val gap: GapsSpec = GapsSpec()
) {}

@Serializable
data class Frame(
    val x: Int, val y: Int, val w: Int, val h: Int
) {
    val asVec: Vec2i get() = Vec2i(x, y)
    val asVec4: Vec4i get() = Vec4i(x, y, w, h)
}

class ImageAtlas(val name: String) {
    val tex = mutableStateOf<Texture2d?>(null)
    val frames = mutableMapOf<String, Frame>()
    lateinit var tileCoords: Array<Vec4i>
    val nameIndex = mutableMapOf<String, Int>()

    suspend fun load(spec: ImageAtlasSpec, assets: AssetManager) {
        val tilesTexPath = "sprites/${spec.tileset.path}/$name.png"
        val tilesMapPath = "sprites/${spec.tileset.path}/$name.json"

//        val texData = assets.loadTextureAtlasData(tilesTexPath, spec.tileWidth, spec.tileHeight)
        //withContext()

        val serializer = serializer<Frame>()
        val content = assets.loadAsset(tilesMapPath)!!.toArray().decodeToString()
        val framesObj = Json.decodeFromString<JsonObject>(content)
        val res = mutableMapOf<String, Frame>()

        (framesObj["frames"] as JsonObject).forEach { entry ->
            res[entry.key] = Json.decodeFromString(serializer, (entry.value as JsonObject)["frame"].toString())
        }

        // we get real tilesizes here
//        with(res[res.keys.first()]!!) {
//            spec.tileWidth = w
//            spec.tileHeight = h
//        }

        val loadedTexture = assets.loadAndPrepareTexture(tilesTexPath, simpleTextureProps)
        val tilesInRow = loadedTexture.loadedTexture!!.width / spec.tileWidth.toFloat()

        println("tiles in row = $tilesInRow")

        val sortedByXYKeys = res.keys.sortedBy { res[it]!!.y * tilesInRow + res[it]!!.x }

        frames.clear()
        frames.putAll( sortedByXYKeys.associateWith { res[it]!! } )
        sortedByXYKeys.forEachIndexed { i, s -> nameIndex[s] = i }
        tileCoords = frames.map { it.value.asVec4 }.toTypedArray()

        // done
        tex.set( loadedTexture )
    }

    fun getTexOffset(frameName: String) = frames[frameName]!!.asVec
    fun getTexOffset(frameIndex: Int): Vec2i = Vec2i(tileCoords[frameIndex].x, tileCoords[frameIndex].y)
    fun getFrame(frameIndex: Int): Vec4i = tileCoords[frameIndex]
}

data class Region(val x: Double, val y: Double, val w: Double, val h: Double)

data class ImageAtlasDataSpec(
    val tilesX: Int, val tilesY: Int, val tileWidth: Int, val tileHeight: Int,
    val framesProvider: (i: Int) -> Region = {i ->
        Region( x = (i % tilesX).toDouble(),
            y = (i / tilesY).toDouble(),
            w = tileWidth.toDouble(),
            h = tileHeight.toDouble()
        )
    }
)
