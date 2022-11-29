import de.fabmax.kool.AssetManager
import de.fabmax.kool.util.Log
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import me.az.utils.logd

class AnimationFrames(
    val atlasId: Int,
    private val animsFileName: String
) {

    val sequence = mutableMapOf<String, List<Int>>() // duration to frame

    suspend fun appendFrom(assets: AssetManager, tileSpec: ImageAtlasSpec, fileName: String) {
        val oldl = Log.level
        Log.level = Log.Level.OFF
        val newseq =
            try {
                assets.loadAndPrepareAnims("sprites/${tileSpec.tileset.path}/anims/$fileName.json")
            } catch (e: Throwable) {
                try {
                    assets.loadAndPrepareAnims("anims/$fileName.json")
                } catch (e: Throwable) {
                    mapOf()
                }
            }
        Log.level = oldl
        // key collide strategy
        // stub
        newseq.forEach {(k ,v) ->
            if ( sequence.containsKey(k) ) throw IllegalStateException("${animsFileName}: $k")
            sequence[k] = v
        }
    }

    suspend fun loadAnimations(tileSpec: ImageAtlasSpec, assetMgr: AssetManager) {
        sequence.clear()
        appendFrom(assetMgr, tileSpec, animsFileName)
        logd { "loaded anims: ${sequence.map { "${it.key} len: ${it.value.size}" }.joinToString("\n")}" }
    }

    private suspend fun AssetManager.loadAndPrepareAnims(assetPath: String): Map<String, List<Int>> {
        //everything here - breaks. very fragile
        val root = Json.decodeFromString<JsonObject>(loadAsset(assetPath)!!.toArray().decodeToString())
        val frames  = root["sequence"] as JsonObject
        return frames.keys.associateWith { k ->
            when(frames[k]) {
                is JsonArray -> (frames[k] as JsonArray).map { it.jsonPrimitive.int }
                is JsonObject -> (frames[k] as JsonObject).run {
                    val dur = (this["durations"] as JsonArray).map { it.jsonPrimitive.int }
                    val _fr = (this["frames"] as JsonArray).map { it.jsonPrimitive.int }
                    dur.flatMapIndexed { index, d -> Array(d) { _fr[index] }.toList() }
                }
                else -> throw RuntimeException("assetPath=$assetPath gives: ${frames[k]}")
            }
        }
    }
}