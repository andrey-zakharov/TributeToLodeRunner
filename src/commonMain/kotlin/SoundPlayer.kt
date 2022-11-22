import de.fabmax.kool.AssetManager
import de.fabmax.kool.modules.audio.AudioClip
import de.fabmax.kool.modules.audio.WavFile
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.az.ilode.Sound

class SoundPlayer(private val assets: AssetManager) {
    private val sounds = mutableMapOf<String, AudioClip>()
    val bank = mutableMapOf<Sound, WavFile>()

    suspend fun loadSounds() {
        val soundsDir = "sounds/ap2"
        val soundsJsonPath = "$soundsDir/sounds.json"
        val content = assets.loadAsset(soundsJsonPath)!!.toArray().decodeToString()
        val clipsObj = Json.decodeFromString<JsonObject>(content)
        sounds.putAll( (clipsObj["clips"] as JsonObject).map {
            val name = it.key
            val ext = ((it.value as JsonObject)["type"] as JsonPrimitive).content
            name to assets.loadAudioClip("$soundsDir/$name.$ext").also { clip->
                clip.masterVolume = 0.2f
            }
        }.toMap())

        bank[Sound.DIG] = WavFile(assets.loadAsset(soundsDir + "/" + Sound.DIG.fileName + ".wav")!!)

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
    operator fun set(s: String, clip: AudioClip) = sounds.set(s, clip)
}