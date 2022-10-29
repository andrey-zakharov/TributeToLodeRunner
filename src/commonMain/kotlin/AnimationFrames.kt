import de.fabmax.kool.AssetManager
import de.fabmax.kool.KoolContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*

class AnimationFrames(
    private val asset: String,
    private val tryDir: String = "."
) {

    val sequence = mutableMapOf<String, List<Int>>()

    suspend fun loadAnimations(tileSpec: ImageAtlasSpec, assetMgr: AssetManager) {
        val assetData =
            assetMgr.loadAsset("sprites/${tileSpec.tileset.path}/anims/$asset.json") ?:
            assetMgr.loadAsset("anims/$asset.json")
        assetData?.run {
            val root = Json.decodeFromString<JsonObject>(toArray().decodeToString())
            // validate json
            val frames  = root["sequence"] as JsonObject
            frames.keys.forEach {
                sequence[it] = (frames[it]!! as JsonArray).map { it.jsonPrimitive.int }
            }
        }
        println("loaded anims: ${sequence.map { "${it.key} len: ${it.value.size}" }.joinToString("\n")}")
    }
}