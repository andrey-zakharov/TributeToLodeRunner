package me.az.ilode

import AppContext
import GameSpeed
import LevelSet
import SoundPlayer
import TileSet
import com.russhwolf.settings.Settings
import com.russhwolf.settings.boolean
import com.russhwolf.settings.float
import com.russhwolf.settings.int
import de.fabmax.kool.KeyCode
import de.fabmax.kool.LocalKeyCode
import de.fabmax.kool.modules.ui2.MutableStateValue
import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.pipeline.RenderPass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import me.az.utils.StackedState
import me.az.utils.buildStateMachine
import me.az.utils.enumDelegate
import kotlin.coroutines.CoroutineContext
import kotlin.properties.ReadWriteProperty

//class IntProperty(key: String? = null, defaultValue: Int, minValue: Int?, maxValue: Int?): ReadWriteProperty<Any?, Int> {
//
//}

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


enum class Sound(private val fileNameO: String? = null, private val fileNameCall: (() -> String)? = null) {
    BORN, DEAD, DIG, DOWN, FALL, GOLD("getGold"),
    FINISH("goldFinish"),
    PASS,
    TRAP
    ;
    val fileName get() = fileNameO ?: fileNameCall?.invoke() ?: name.lowercase()
}
//system
class Game(val state: AppContext) : CoroutineScope {
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

    val gameOver get() = runner.health <= 0 // classic
    // hack to sync anims
    var animEnds = false
    var skipAnims = false

//    private var gameState = GameState.GAME_START
//        set(value) {
//            field = value
////            onStatusChanged.forEach { it.invoke(field) }
//        }
    val isPlaying get() = fsm.currentStateName == GameState.GAME_RUNNING
    val isPaused get() = fsm.currentStateName == GameState.GAME_PAUSE
    val isOver get() = fsm.currentStateName == GameState.GAME_OVER_ANIMATION || fsm.currentStateName == GameState.GAME_OVER

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

    lateinit var soundPlayer: SoundPlayer
    fun playSound(sound: Sound, x: Int, y: Int, force: Boolean = false) =
        soundPlayer.playSound(sound.fileName, false, force)

    fun stopSound(sound: Sound) =
        soundPlayer.stopSound(sound.fileName)

    fun startGame() { fsm.reset(true) }
    //@ActionSpec(defaultSpec = KeySpec('a'+ctrl))
    fun abortGame() { fsm.setState(GameState.GAME_START) }

    //@ActionSpec(defaultSpec = KeySpec('f'+ctrl))
    fun finishGame() = fsm.setState(GameState.GAME_FINISH) // cheat
    fun prevLevel() = fsm.setState(GameState.GAME_PREV_LEVEL) // cheat
    fun nextLevel() = fsm.setState(GameState.GAME_NEXT_LEVEL) // cheat
    fun overGame() = fsm.setState(GameState.GAME_OVER_ANIMATION) // cheat

    fun reset() {
        nextGuard = 0
        nextMoves = 0
//        runner?.reset()
        fsm.reset(true)
    }

    fun finish() = fsm.finish()

    private val fsm by lazy { buildStateMachine(GameState.GAME_NEW_LEVEL) {
        state(GameState.GAME_START) {
            edge(GameState.GAME_RUNNING) {
                validWhen { runner.anyKeyPressed }
                // show intro
            }
            onEnter {
                animEnds = false
                level!!.reset()
                onLevelStart.forEach { it.invoke(level!!) }
            }
        }
        state(GameState.GAME_RUNNING) {
            edge(GameState.GAME_FINISH) {
                validWhen { runner.success }
            }
            edge(GameState.GAME_RUNNER_DEAD) {
                validWhen { !runner.alive && runner.health > 1 }
            }
            edge(GameState.GAME_OVER_ANIMATION) {
                validWhen { !runner.alive && runner.health <= 1 }
            }
            onUpdate {

                if (playGame() && level?.status == GameLevel.Status.LEVEL_PLAYING) {
                    level?.showHiddenLadders()
                    playSound(Sound.FINISH, runner.x, runner.y)
                }

                null
            }
            onExit {
                skipAnims = false
                // stop all sounds actually
                runner.stopSound(Sound.FALL)
            }
        }

        state(GameState.GAME_RUNNER_DEAD) {
            onEnter {
                playSound(Sound.DEAD, runner.x, runner.y)
                runner.health--

                animEnds = false
            }
            edge(GameState.GAME_OVER_ANIMATION) {
                validWhen { state.runnerLifes.value <= 0 }
            }
            edge(GameState.GAME_NEW_LEVEL) {
                validWhen { animEnds || skipAnims }
            }
        }

        state(GameState.GAME_FINISH) {
            var finalScore = 0
            onEnter {
                playSound(Sound.PASS, runner.x, runner.y)
                // playMode = CLASSIC
                finalScore = runner.score + SCORE_COMPLETE
                Unit
//                gameState = GameState.GAME_FINISH_SCORE_COUNT
            }

            edge(GameState.GAME_NEXT_LEVEL) {
                validWhen {
                    runner.score >= finalScore
                }
            }

            onUpdate {
                runner.addScore(SCORE_COMPLETE_INC)
                null
            }
        }
        state(GameState.GAME_PREV_LEVEL) {
            onEnter { animEnds = false }
            // handled above
            edge(GameState.GAME_NEW_LEVEL) { validWhen { animEnds || skipAnims } }
        }
        state(GameState.GAME_NEXT_LEVEL) {
            onEnter {
                animEnds = false
                runner.health += 1
            }
            // handled above
            edge(GameState.GAME_NEW_LEVEL) { validWhen { animEnds || skipAnims } }
        }

        state(GameState.GAME_NEW_LEVEL) {

            edge(GameState.GAME_START) {
                validWhen { level?.status == GameLevel.Status.LEVEL_PLAYING }
            }

            onExit {

            }
        }

        state(GameState.GAME_OVER_ANIMATION) {
            // final state. exit to menu by external code or start new
            onEnter {
                runner.health = 0
                playSound(Sound.DEAD, runner.x, runner.y)
                animEnds = false // animEnds - used by shatter, always true :)
                level?.status = GameLevel.Status.LEVEL_PAUSED
                // show hiscores?
            }
            // pause all game until anim
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

    val onStateChanged = mutableListOf<StackedState<GameState, Game>.() -> Unit>()

    val onPlayGame = mutableListOf<(game: Game, ev: RenderPass.UpdateEvent?) -> Unit>()

    private fun playGame(ev: RenderPass.UpdateEvent? = null): Boolean {
        return level?.run {
            runner.update()
            if ( !state.stopGuards.value ) guardsUpdate()
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
           if ( nextGuard >= guards.size - 1 ) {
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

typealias KeyMod = Int

data class InputSpec(val code: KeyCode, val modificatorBitMask: Int)
fun KeyCode.toInputSpec(vararg mod: KeyMod) = InputSpec(this, mod.sum())
fun Char.toInputSpec(vararg mod: KeyMod) = LocalKeyCode(this).toInputSpec(*mod)
