package me.az.view

import AnimationFrames
import ImageAtlas
import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.Vec2d
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.math.clamp
import de.fabmax.kool.modules.audio.AudioOutput
import de.fabmax.kool.modules.audio.MixNode
import de.fabmax.kool.modules.audio.WavFile
import de.fabmax.kool.modules.audio.WavNode
import de.fabmax.kool.modules.audio.synth.Oscillator
import de.fabmax.kool.modules.audio.synth.SampleNode
import de.fabmax.kool.modules.audio.synth.Wave
import de.fabmax.kool.scene.Node
import de.fabmax.kool.util.logW
import me.az.ilode.Actor
import me.az.ilode.ActorEvent
import me.az.ilode.Sound
import me.az.scenes.Sequences
import me.az.utils.lerp
import me.az.utils.logd

class ActorView(
    private val actor: Actor,
    private val spriteSystem: SpriteSystem,
    private val anims: AnimationFrames,
    val tileSize: Vec2i,
    name: String? = null,
    soundsBank: Map<Sound, WavFile>
) : Node(name)/* : Sprite3d(tileSize, atlas.tex.value, atlas.getTileSize(), name)*/ {

    private val fallSound by lazy { FallSound() }
    private val clips = mapOf(
        Sound.FALL.fileName to fallSound,
        Sound.DIG.fileName to WavNode(soundsBank[Sound.DIG]!!).also { it.loop = false }
    )

    val mainVolume = MixNode()
    private val audioOutput = AudioOutput().also {
        it.mixer.addNode(mainVolume)
        clips.forEach { (k, v) ->
            mainVolume.addNode(v, 0f)
        }
        mainVolume.gain = 0.2f
    }

    private fun updateFallFreq() {
        fallSound.height =
            actor.absolutePosY.toFloat() / (actor.level.height * tileSize.y.toFloat())

    }
    private fun Actor.levelPos() = Vec2d(
        (x - level.width / 2.0 + 0.5) * 1f + (actor.ox.toDouble() / tileSize.x),
        (level.height - y + 0.5) * 1f - (actor.oy.toDouble() / tileSize.y))
    .toVec2f()

    val instance = spriteSystem.sprite(anims.atlasId, actor.levelPos(), 0)

    init {
//        addDebugAxis()
        onUpdate += {
            anims.sequence[actor.action.id]?.run {
                // back hack
                if ( actor.sequenceSize <= 0 ) actor.sequenceSize = size
                // CPU waste
                instance.atlasId.set( anims.atlasId )
                instance.tileIndex.set(this[actor.frameIndex.mod(actor.sequenceSize)])
            }

            instance.pos.set(actor.levelPos())
            instance.onPosUpdated()
            updateFallFreq()
            //setIdentity()
            //translate(
            //    actor.x - actor.level.width / 2.0 + 0.5, actor.level.height - actor.y - 0.5,
            //    0.0
            //)

//            scale(1f, 1f, 1f )
            //translate(actor.ox.toDouble() / tileSize.x, -actor.oy.toDouble() / tileSize.y, 0.0)

        }
//        actor.game.onPlayGame += { g, ev ->
//            sequences[actor.action.id]?.run {
//                val (atlasId, seq) = this
//                println("${actor.action} ${seq[actor.frameIndex.mod(seq.size)]}")
//            }
//        }

        actor.onEvent += {

            when(it) {
                is ActorEvent.StartSound -> {
                    // update x and y where?
                    if ( !clips.containsKey(it.soundName) ) {
                        println("no sound ${it.soundName}")
                    } else {
                        val clip = clips[it.soundName]
                        val idx = clips.values.indexOf(clip)
                        mainVolume.inputGains[idx] = 1f
                        updateFallFreq()

                        when( clip ) {
                            is WavNode -> clip.pos = 0.0
                            is FallSound -> {

                            }
                        }
                    }
                }
                is ActorEvent.StopSound -> {
                    logd { "stopping sound '${it.soundName}'" }
                    if ( !clips.containsKey(it.soundName) ) {
                        logW { "no sound ${it.soundName}" }
                    } else {
                        val idx = clips.values.indexOf(clips[it.soundName])
                        mainVolume.inputGains[idx] = 0f
                    }
                }
            }
        }
    }

    override fun dispose(ctx: KoolContext) {
        super.dispose(ctx)
        spriteSystem.sprites.remove(instance)
        spriteSystem.dirty = true
        audioOutput.close()
        logd { "audio closed" }
    }
}

private const val noteDurMs = 60f / 1000 // or 164 waves... hm
// idea is that this sound impulse should be different too for different freqs
private const val phaseShiftDur = noteDurMs / 2
private const val phaseShift = noteDurMs / 2
private const val longGapMs = 30f / 1000
private const val shortGapMs = 15f / 1000 *  + noteDurMs


class FallSound : SampleNode() {

    // 2741 Гц (F7) = -13,3 дБ
    // 2734 Гц (F7) = -12,0 дБ
    // 2579 Гц (E7) = -15,1 дБ
    // 2580 Гц (E7) = -14,9 дБ
    // 2477 Гц (D♯7) = -13,4 дБ
    // 2355 Гц (D7) = -16,2 дБ
    // max note = 100

    // note (nN) = 45ms + 1ms gap + 45ms
    // n1 + long gap + n2 + long gap + n3 + shot gap + n4 + long gap + n5...
    // think that gaps are from CPU cycles needed to move runner from pos to pos.
    // anyway emulate 1 bit sound from 1980 by oscillators of square waves, because of. why not?

    // make pitch alter for wav (any) node
    var height = 0f // till 1f
        set(value) {
            field = value
            freq = value.lerp(startFreq, endFreq)
        }
    var octave = 5
    var percFreqMultiplier = 1f / 250f


    val startFreq = note(6, 5)
    val endFreq = note(-10, 5)
    var freq = startFreq
        set(value) {
            field = value
            noteDur = value / freq * noteDurMs
        }
    private var noteDur = startFreq / freq * noteDurMs
    private var shortGap = startFreq / freq * shortGapMs + noteDur

    fun dis() {
        println("height = $height octave = $octave freq=${freq} noteDur=$noteDur")
    }

//    private val osc = Oscillator(Wave.SQUARE).apply { gain = 1f }

    private val osc = Oscillator(Wave.SINE).apply { gain = 2f }
    private val ph = Oscillator(Wave.SINE).apply { gain = 1f }
    override fun generate(dt: Float): Float {
        return (osc.next(dt, freq) * ph.next(dt, freq * percFreqMultiplier)).clamp(-0.8f, 0.5f)
    }

    companion object {
        init {
            // C4 is 0 note of 2 octave (261.62555 Hz)
//            print("octaves: ")
//            for( o in -5 .. 9) {
//                print(o.toString().padStart(12, ' '))
//            }
//            println()
//            for ( n in -20 .. 79 ) {
//                print("note $n  ")
//                for( o in -5 .. 9) {
//                    print("%.5f ".format(note(n,o)).padStart(12, ' '))
//                }
//                println()
//            }
        }
    }

}
