package me.az.ilode

import AnimationFrames
import GameSpeed
import LevelSet
import SoundPlayer
import TileSet
import com.russhwolf.settings.*
import de.fabmax.kool.*
import de.fabmax.kool.modules.ui2.MutableStateValue
import de.fabmax.kool.pipeline.RenderPass
import kotlinx.coroutines.*
import me.az.utils.StackedState
import me.az.utils.buildStateMachine
import me.az.utils.enumDelegate
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmInline
import kotlin.random.Random


class GameSettings(val settings: Settings) {
    var curScore: Int by settings.int(defaultValue = 0)
    var currentLevel: Int by settings.int(defaultValue = 0)
    var runnerLifes by settings.int(defaultValue = START_HEALTH) // max = MAX_HEALTH
    var speed: GameSpeed by enumDelegate(settings, defaultValue = GameSpeed.SPEED_SLOW)
    var spriteMode: TileSet by enumDelegate(settings, defaultValue = TileSet.SPRITES_APPLE2)
    var version: LevelSet by enumDelegate(settings, defaultValue = LevelSet.CLASSIC)
    var introDuration by settings.float(defaultValue = 60f)
    var sometimePlayInGodMode by settings.boolean()

    // { s:curScore, l:curLevel, r:runnerLife, m: maxLevel, g: sometimePlayInGodMode, p: passedLevel};
    var immortal by settings.boolean(defaultValue = true)
    var stopGuards by settings.boolean(defaultValue = false)
}
const val SCORE_COUNTER = 15 // how much scores bumbs in finish anim
const val SCORE_COMPLETE = 1500
const val SCORE_COMPLETE_INC = SCORE_COMPLETE / SCORE_COUNTER
const val SCORE_GOLD     = 250
const val SCORE_FALL     = 75
const val SCORE_DIES     = 75

//system
class Game(val state: GameSettings) : CoroutineScope {
    sealed class GameEvent {
        object Tick: GameEvent()
        class AnimationEnds(val animName: String): GameEvent()
    }
//    val onStatusChanged = mutableListOf<(GameState) -> Unit>()
    val onLevelStart = mutableListOf<(GameLevel) -> Unit>()
    // { s:curScore, l:curLevel, r:runnerLife, m: maxLevel, g: sometimePlayInGodMode, p: passedLevel};
    var runner: Runner = Runner(this)
    val guards = mutableListOf<Guard>()

    var nextGuard = 0
    var nextMoves = 0
    val stopAnims = MutableStateValue(false)
    val stopGuards = MutableStateValue(state.stopGuards).also {
        it.onChange { v -> state.stopGuards = v }
    }
    val gameOver get() = runner.health <= 0 // classic
    // hack to sync anims
    var animEnds = false
    var skipAnims = false

//    private var gameState = GameState.GAME_START
//        set(value) {
//            field = value
////            onStatusChanged.forEach { it.invoke(field) }
//        }
    val isPlaying get() = fsm.currentStateName == "run"
    val isPaused get() = fsm.currentStateName == "pause"

    var level: GameLevel? = null
        set(v) {
            field = v
            v?.run {
                guards.clear()
                level!!.guardsPos.forEach {
                    val g = Guard(this@Game)
                    g.block.set(it)
                    guards.add(g)
                }
                onLevelStart.forEach { it.invoke(this) }
                dirty = true
                status = GameLevel.Status.LEVEL_PLAYING
            }
        }

    fun startGame() { fsm.reset(true) }
    //@ActionSpec(defaultSpec = KeySpec('a'+ctrl))
    fun abortGame() { fsm.setState("dead") }

    //@ActionSpec(defaultSpec = KeySpec('f'+ctrl))
    fun finishGame() { fsm.setState("finish") }
    fun prevLevel() { fsm.setState("prevlevel") }
    fun nextLevel() { fsm.setState("nextlevel") }

    fun reset() {
        nextGuard = 0
        nextMoves = 0
//        runner?.reset()
        fsm.reset(true)
    }

    private val fsm by lazy { buildStateMachine<Game>("newlevel") {
        state("start") {
            edge("run") {
                validWhen { runner.anyKeyPressed }
                // show intro
            }
            onEnter {
                animEnds = false
                level!!.reset()
            }
        }
        state("run") {
            edge("finish") {
                action {
//                    runner.sounds.playSound("pass")
                }
                validWhen { runner.success }
            }
            edge("dead") {
                validWhen { !runner.alive }
            }
            onUpdate {

                if (playGame() && level?.status == GameLevel.Status.LEVEL_PLAYING)
                    level?.showHiddenLadders()

                null
            }
            onExit {
                skipAnims = false
                runner.sounds.stopSound("fall") // ?
            }
        }

        state("dead") {
            onEnter {
                runner.sounds.playSound("dead")
                runner.health--

                animEnds = false
            }
            edge("gameover") {
                validWhen { state.runnerLifes <= 0 }
            }
            edge("newlevel") {
                validWhen { animEnds || skipAnims }
            }
        }

        state("finish") {
            var finalScore = 0
            onEnter {
                // playMode = CLASSIC
                finalScore = runner.score + SCORE_COMPLETE
                Unit
//                gameState = GameState.GAME_FINISH_SCORE_COUNT
            }

            edge("nextlevel") {
                validWhen {
                    runner.score >= finalScore
                }
            }

            onUpdate {
                runner.addScore(SCORE_COMPLETE_INC)
                runner.sounds.playSound("pass")
                null
            }
        }
        state("prevlevel") {
            onEnter { animEnds = false }
            // handled above
            edge("newlevel") { validWhen { animEnds || skipAnims } }
        }
        state("nextlevel") {
            onEnter { animEnds = false }
            // handled above
            edge("newlevel") { validWhen { animEnds || skipAnims } }
        }

        state("newlevel") {

            edge("start") {
                validWhen { level?.status == GameLevel.Status.LEVEL_PLAYING }
            }

            onExit {

            }
        }

        this.onStateChanged += { this@Game.onStateChanged.forEach { it(this) }}
    } }

    fun tick( ev: RenderPass.UpdateEvent? ) {
        fsm.update(this)
    }

    protected val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job

    init {

        async {
            delay(100)
        }

    }

    val onStateChanged = mutableListOf<StackedState<Game>.() -> Unit>()

    val onPlayGame = mutableListOf<(game: Game, ev: RenderPass.UpdateEvent?) -> Unit>()

    private fun playGame(ev: RenderPass.UpdateEvent? = null): Boolean {
        return level?.run {
            runner.update()
            if ( !stopGuards.value ) guardsUpdate()
            if ( !stopAnims.value ) {
                level?.update(runner)
            }
            onPlayGame.forEach { it.invoke(this@Game, ev) }

            isDone
        } ?: false
    }

    private fun guardsUpdate() {
        if ( guards.isEmpty() ) return

        // some old AI?
        if ( nextMoves == 2 ) {
            nextMoves = 0
        } else {
            nextMoves ++
        }

        var movesCount = if ( level?.status == GameLevel.Status.LEVEL_STARTUP ) {
            guards.size
        } else {
            Guard.guardsMoves[guards.size][nextMoves]
        }

        while ( movesCount > 0 ) {
           if ( nextGuard == guards.size - 1 ) {
               nextGuard = 0
           } else {
               nextGuard ++
           }

           with(guards[nextGuard]) {
               updateGuard(runner)
           }

            movesCount--
        }
    }

    fun getGuard(x: Int, y: Int) = guards.first { it.block.x == x && it.block.y == y }

}
enum class GameState {
    GAME_START,
    GAME_RUNNING ,
    GAME_FINISH , GAME_FINISH_SCORE_COUNT,
    GAME_WAITING , GAME_PAUSE,
    GAME_NEW_LEVEL , GAME_RUNNER_DEAD,
    GAME_OVER_ANIMATION , GAME_OVER,
    GAME_NEXT_LEVEL , GAME_PREV_LEVEL,
    GAME_LOADING , GAME_WIN
}
typealias KeyMod = Int

data class InputSpec(val code: KeyCode, val modificatorBitMask: Int)
fun KeyCode.toInputSpec(vararg mod: KeyMod) = InputSpec(this, mod.sum())
fun Char.toInputSpec(vararg mod: KeyMod) = LocalKeyCode(this).toInputSpec(*mod)
