import de.fabmax.kool.KoolContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*

class AnimationFrames(
    val asset: String
) {

    val sequence = mutableMapOf<String, List<Int>>()

    suspend fun loadAnimations(ctx: KoolContext) {
        ctx.assetMgr.loadAsset("./anims/$asset.json")?.run {
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