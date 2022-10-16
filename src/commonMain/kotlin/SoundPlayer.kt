import de.fabmax.kool.AssetManager
import de.fabmax.kool.modules.audio.AudioClip
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

interface AudioContext {
    val pan: Int // for node pos x, y
//    val x: Float
//    val y: Float
    val clip: AudioClip
    fun play()
    fun stop()
}

class SoundPlayer(private val assets: AssetManager) {
    private val sounds = mutableMapOf<String, AudioClip>()

    suspend fun loadSounds() {
        val soundsDir = "sounds/ap2/"
        val soundsJsonPath = "$soundsDir/sounds.json"
        val content = assets.loadAsset(soundsJsonPath)!!.toArray().decodeToString()
        val clipsObj = Json.decodeFromString<JsonObject>(content)
        sounds.putAll( (clipsObj["clips"] as JsonObject).map {
            val name = it.key
            val ext = ((it.value as JsonObject)["type"] as JsonPrimitive).content
            it.key to assets.loadAudioClip("$soundsDir/$name.$ext")
        }.toMap())

        sounds.forEach { (name, clip) ->

        }
    }

    fun playSound(s: String, looped: Boolean = false, playSingle: Boolean = true) {
        sounds[s]?.run {
            if ( playSingle && !isEnded ) return
            if ( !isEnded ) stop() // or put over
            play()
        }
    }

    fun stopSound(s: String, delay: Float = 0f) = sounds[s]?.stop()

    operator fun get(s: String) = sounds[s]
}