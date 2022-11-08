import de.fabmax.kool.AssetManager
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.math.Vec4i
import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.scene.geometry.RectProps
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

enum class TileSet(
    val path: String,
    val tileWidth: Int = 20,
    val tileHeight: Int = 22
) {
    SPRITES_APPLE2("ap2"), // 560 x 384, 28 wide levels (20x22 tiles)
    SPRITES_ATARI8BIT("a8b"),
    SPRITES_COMMODORE64("c64"),
    SPRITES_ZXSPECTRUM("zxs"),
    SPRITES_IBM("ibm", 24, 22), // has 640x400 original and 26-tiles wide levels (24x20 tiles)
    SPRITES_NEC("nec", 24, 20), // has 640x400 original and 26-tiles wide levels
    SPRITES_NES("nes"),
    ;
    val dis get() = name.removePrefix("SPRITES_")
}
@kotlinx.serialization.Serializable
data class AtlasGeometry(val cols: Int, val rows: Int) {
    companion object {
        fun from(e: JsonElement) = from(serializer<AtlasGeometry>(), e)
        fun from(serializer: DeserializationStrategy<AtlasGeometry>, e: JsonElement) = Json.decodeFromString(serializer, e.toString())
    }
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
    fun getIndex(geometry: AtlasGeometry) = // in atlas
        ( x / w ) + ( y / h * geometry.cols )

    companion object {
        fun from(e: JsonElement) = from(serializer<Frame>(), e)
        fun from(serializer: DeserializationStrategy<Frame>, e: JsonElement) =
            Json.decodeFromString(serializer, e.toString())
    }
}

class ImageAtlas(val name: String, whenLoaded: () -> Unit = {}) {

    protected val job = Job()
    val tex = mutableStateOf<Texture3d?>(null)
    var geometry: AtlasGeometry? = null
    val tileWidth get() = tex.value?.loadedTexture?.width  ?: 0
    val tileHeight get() = tex.value?.loadedTexture?.height ?: 0
    val nameIndex = mutableMapOf<String, Int>()

    fun getTileSize() = Vec2i(tileWidth, tileHeight)
    suspend fun load(tileset: TileSet, assets: AssetManager) {
        assets.loadGeom(tileset)
        val newTex = assets.loadAtlas(tileset, geometry!!)
        tex.set(newTex)
    }

    private suspend fun AssetManager.loadGeom(tileset: TileSet) {
        val tilesMapPath = "sprites/${tileset.path}/$name.json"
        val content = loadAsset(tilesMapPath)!!.toArray().decodeToString()
        val jsonObj = Json.decodeFromString<JsonObject>(content)
        if ( !jsonObj.containsKey("geom") ) throw IllegalStateException("no geom for atlas ${tileset.dis} $name")
        geometry = AtlasGeometry.from(jsonObj["geom"]!!)

        nameIndex.clear()
        (jsonObj["frames"] as? JsonObject)?.forEach { entry ->
            val ord: Int = when(entry.value) {
                is JsonObject -> Frame.from(entry.value.jsonObject.get("frame")!!).getIndex( geometry!! )
                is JsonPrimitive -> (entry.value as JsonPrimitive).int
                JsonNull -> TODO()
                is JsonArray -> TODO()
            }
            nameIndex[entry.key] = ord
        }
        println("NAMES=$nameIndex")
    }
    private suspend fun AssetManager.loadAtlas(tileset: TileSet, geometry: AtlasGeometry): Texture3d {
        val tilesTexPath = "sprites/${tileset.path}/$name.png"
        val data = loadTextureAtlasData(tilesTexPath, geometry.cols, geometry.rows, simpleTextureProps.format)
        return Texture3d(simpleTextureProps, name, BufferedTextureLoader(data))
    }
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
