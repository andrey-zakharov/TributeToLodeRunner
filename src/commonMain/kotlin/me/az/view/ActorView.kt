package me.az.view

import AnimationFrames
import ImageAtlas
import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.*
import de.fabmax.kool.modules.audio.AudioOutput
import de.fabmax.kool.modules.audio.MixNode
import de.fabmax.kool.modules.audio.WavFile
import de.fabmax.kool.modules.audio.WavNode
import de.fabmax.kool.modules.audio.synth.Oscillator
import de.fabmax.kool.modules.audio.synth.SampleNode
import de.fabmax.kool.modules.audio.synth.Wave
import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.animation.Animator
import de.fabmax.kool.scene.animation.InterpolatedFloat
import de.fabmax.kool.scene.animation.InverseSquareAnimator
import de.fabmax.kool.util.logW
import me.az.ilode.Actor
import me.az.ilode.ActorEvent
import me.az.ilode.Sound
import me.az.scenes.Sequences
import me.az.utils.lerp
import me.az.utils.logd
import me.az.utils.mul

class ActorView(
    private val actor: Actor,
    private val spriteSystem: SpriteSystem,
    private val anims: AnimationFrames,
    name: String? = null,
    soundsBank: Map<Sound, WavFile>
) : Node(name)/* : Sprite3d(tileSize, atlas.tex.value, atlas.getTileSize(), name)*/ {

    private val scaleX get() = spriteSystem.transform[0, 0].toFloat()
    private val scaleY get() = spriteSystem.transform[1, 1].toFloat()

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
            actor.absolutePosY.toFloat() / (actor.level.height * scaleY)

    }

    private val tmpPos = Mat4f()

    private fun Actor.levelPos(): Mat4f {
        tmpPos.set(modelMat).translate(
            actor.x + actor.ox / scaleX,
            actor.y + actor.oy / scaleY,
            0f
        ).scale(1f, -1f, 1f)
            .translate(0.5f, 0.5f, 1f)
            .scale(scaleAnimator.value.value)
            .translate(-0.5f, -0.5f, -0.5f)
        //spriteSystem.toLocalCoords(tmpPos)
        return tmpPos
    }

    // for blinking
    private val scaleAnimator = InverseSquareAnimator(InterpolatedFloat(0f, 1f)).apply {
        duration = 0.2f
        repeating = Animator.REPEAT_TOGGLE_DIR
    }
    fun startBlink() {
        scaleAnimator.speed = 1f
        scaleAnimator.progress = 0f
    }
    fun stopBlink() {
        scaleAnimator.speed = 0f
        scaleAnimator.progress = 1f
        scaleAnimator.value.value = 1f
    }

    // when view created, there is no parent, and modelMat is wrong (zeroed)
    val instance by lazy { spriteSystem.sprite(anims.atlasId, 0, actor.levelPos()) }

    init {

        stopBlink()
//        addDebugAxis()
        onUpdate += {
            if ( scaleAnimator.speed != 0f ) {
                scaleAnimator.tick(it.ctx)
                spriteSystem.dirty = true
            }
            anims.sequence[actor.action.id]?.run {
                // back hack
                if ( actor.sequenceSize != size ) actor.sequenceSize = size
                // CPU waste
                instance.atlasId.set( anims.atlasId )
//                println("${actor.action.id} ${actor.frameIndex} $this")
                instance.tileIndex.set(this[actor.frameIndex.mod(actor.sequenceSize)])
            }

            updateModelMat()
            //instance.writeModelMat(actor.levelPos())
            instance.modelMat.set(actor.levelPos())
            updateFallFreq()
        }

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
//                    logd { "stopping sound '${it.soundName}'" }
                    if ( !clips.containsKey(it.soundName) ) {
                        logW { "no sound ${it.soundName}" }
                    } else {
                        val idx = clips.values.indexOf(clips[it.soundName])
                        mainVolume.inputGains[idx] = 0f
                    }
                }
            }
        }

        onDispose += {
            spriteSystem.sprites.remove(instance)
            instance.unbind()
            spriteSystem.dirty = true
            audioOutput.close()
            logd { "audio closed" }
        }
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
