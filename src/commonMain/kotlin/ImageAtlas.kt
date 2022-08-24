import de.fabmax.kool.AssetManager
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.scene.geometry.RectProps
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

enum class TileSet(val path: String) {
    SPRITES_APPLE2("ap2"),
    SPRITES_COMMODORE64("c64"),
    SPRITES_IBM("ibm"),
    SPRITES_ATARI8BIT("a8b"),
    SPRITES_ZXSPECTRUM("zxs"),
    SPRITES_NES("nes"),
}

data class ImageAtlasSpec(
    val tileset: TileSet = TileSet.SPRITES_APPLE2,
    val name: String,
    val tileWidth: Int = 20, // +padding?
    val tileHeight: Int = 22,
) {}

@Serializable
data class Frame(
    val x: Int, val y: Int, val w: Int, val h: Int
) {
    val asVec: Vec2i get() = Vec2i(x, y)
}

class ImageAtlas(val spec: ImageAtlasSpec) {
    lateinit var tex: Texture2d
    lateinit var frames: Map<String, Frame>
    lateinit var tileCoords: Array<Vec2i>

    val tilesTexPath get() = "sprites/${spec.tileset.path}/${spec.name}.png"
    val tilesMapPath get() = "sprites/${spec.tileset.path}/${spec.name}.json"
    val nameIndex = mutableMapOf<String, Int>()

    suspend fun load(assets: AssetManager) {

//        val texData = assets.loadTextureAtlasData(tilesTexPath, spec.tileWidth, spec.tileHeight)
        tex = assets.loadAndPrepareTexture(tilesTexPath, simpleTextureProps)
        val serializer = serializer<Frame>()
        val content = assets.loadAsset(tilesMapPath)!!.toArray().decodeToString()
        val framesObj = Json.decodeFromString<JsonObject>(content)
        val res = mutableMapOf<String, Frame>()

        (framesObj["frames"] as JsonObject).forEach { entry ->
            res[entry.key] = Json.decodeFromString(serializer, (entry.value as JsonObject)["frame"].toString())
        }

        val tilesInRow = tex.loadedTexture!!.width / spec.tileWidth
        val sortedByXYKeys = res.keys.sortedBy { res[it]!!.y * tilesInRow + res[it]!!.x }

        frames = sortedByXYKeys.associateWith { res[it]!! }
        sortedByXYKeys.forEachIndexed { i, s -> nameIndex[s] = i }

        tileCoords = frames.map { Vec2i(it.value.x, it.value.y) }.toTypedArray()
    }

    fun getTexOffset(frameName: String) = frames[frameName]!!.asVec
    fun getTexOffset(frameIndex: Int): Vec2i = tileCoords[frameIndex]
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
