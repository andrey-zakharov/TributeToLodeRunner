import de.fabmax.kool.AssetManager
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import me.az.utils.logd

class AnimationFrames(
    private val asset: String,
    private val tryDir: String = "."
) {

    val sequence = mutableMapOf<String, List<Int>>()

    suspend fun loadAnimations(tileSpec: ImageAtlasSpec, assetMgr: AssetManager) {
        sequence.clear()
        sequence.putAll(
            try {
                assetMgr.loadAndPrepareAnims("sprites/${tileSpec.tileset.path}/anims/$asset.json")
            } catch (e: Throwable) {
                assetMgr.loadAndPrepareAnims("anims/$asset.json")
            }
        )

        logd { "loaded anims: ${sequence.map { "${it.key} len: ${it.value.size}" }.joinToString("\n")}" }
    }

    private suspend fun AssetManager.loadAndPrepareAnims(assetPath: String): Map<String, List<Int>> {
        //everything here - breaks. very fragile
        val root = Json.decodeFromString<JsonObject>(loadAsset(assetPath)!!.toArray().decodeToString())
        val frames  = root["sequence"] as JsonObject
        return frames.keys.associateWith { k ->
            (frames[k]!! as JsonArray).map { it.jsonPrimitive.int }
        }
    }
}