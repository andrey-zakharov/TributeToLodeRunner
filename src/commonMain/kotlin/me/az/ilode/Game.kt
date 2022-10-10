package me.az.ilode

import AnimationFrames
import GameSpeed
import LevelSet
import TileSet
import com.russhwolf.settings.*
import de.fabmax.kool.*
import de.fabmax.kool.pipeline.RenderPass
import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.animation.InterpolatedFloat
import de.fabmax.kool.scene.animation.LinearAnimator
import me.az.utils.enumDelegate
import org.mifek.wfc.utils.EventHandler
import kotlin.jvm.JvmInline
import kotlin.math.sqrt
import kotlin.random.Random


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

class GameSettings(val settings: Settings) {
    var spriteMode: TileSet by enumDelegate(settings, defaultValue = TileSet.SPRITES_APPLE2)
    var version: LevelSet by enumDelegate(settings, defaultValue = LevelSet.CLASSIC)
    val startLevel: Int by settings.int(defaultValue = 0)
    var speed: GameSpeed by enumDelegate(settings, defaultValue = GameSpeed.SPEED_SLOW)
    var introDuration by settings.float(defaultValue = 60f)
}
const val SCORE_COUNTER = 15 // how much scores bumbs in finish anim
const val SCORE_COMPLETE = 1500
const val SCORE_GOLD     = 250
const val SCORE_FALL     = 75
const val SCORE_DIES     = 75

//system
class Game(val settings: GameSettings) {
    var stopGuards = false
    var immortal = false
    var speed = settings.speed
    var runner: Runner2 = Runner2(this)
    val guards = mutableListOf<Guard>()

    var nextGuard = 0
    var nextMoves = 0
    var playAnims = false
    var playGuards = true

    val gameOver get() = runner?.health!! <= 0
    private var gameState = GameState.GAME_START
        set(value) {
            field = value
            onStatusChanged.forEach { it.invoke(field) }
        }
    val isPlaying get() = gameState == GameState.GAME_RUNNING
    val isPaused get() = gameState == GameState.GAME_PAUSE

    val onStatusChanged = mutableListOf<(GameState) -> Unit>()
    val onLevelStart = mutableListOf<(GameLevel) -> Unit>()

    var level: GameLevel? = null

    fun levelStartup(level: GameLevel, guardAnims: AnimationFrames) {
        this.level = level
        // playStartAnim
//        runner = Runner(level)
        guards.clear()
        level.guardsPos.forEach {
            val g = Guard(level, anims = guardAnims)
            g.startLevel(level, it)
            guards.add(g)
        }
        runner.startLevel(level)
        level.status = GameLevel.Status.LEVEL_PLAYING
        onLevelStart.forEach { it.invoke(level) }
    }

    fun startGame() { gameState = GameState.GAME_START }
    fun abortGame() { gameState = GameState.GAME_RUNNER_DEAD }

    fun reset() {
        nextGuard = 0
        nextMoves = 0
//        runner?.reset()
        gameState = GameState.GAME_START
    }

    fun tick( ev: RenderPass.UpdateEvent ) {
        when(gameState) {
            GameState.GAME_START -> {
                if (runner.anyKeyPressed) {
                    gameState = GameState.GAME_RUNNING
                    if (level?.isDone == true) level?.showHiddenLadders()
                }
            }

            GameState.GAME_RUNNING -> {
                playGame(ev)
                if (level?.isDone == true) {
                    level?.showHiddenLadders()
                }

                if ( runner.success ) {
                    // enter state
                    gameState = GameState.GAME_FINISH
                }
            }
            GameState.GAME_FINISH -> {
                // stop all sprites anims
                // play mode == PLAY
                runner?.addScore(SCORE_COMPLETE)
                gameState = GameState.GAME_FINISH_SCORE_COUNT


            }

            GameState.GAME_RUNNER_DEAD -> {
//                stopSound
            }
            else -> {
                println(gameState)
            }
        }
    }

    val onPlayGame = mutableListOf<Game.(level: GameLevel, ev: RenderPass.UpdateEvent) -> Unit>()

    fun playGame(ev: RenderPass.UpdateEvent): Boolean {
        return level?.run {
            runner.update(ev)
            if (playGuards) guardsUpdate()
//        if ( playAnims ) {
            level?.update(runner, guards)
//            playAnims = false
//        }

            onPlayGame.forEach { it.invoke(this@Game, this, ev) }
            return isDone
        } ?: false
    }

    fun guardsUpdate() {
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
               if ( !alive ) {
                   // respawn
                   for ( ty in 0 until level.height ) {
                       block.x = Random.nextInt(level.width)
                       block.y = ty
                       if ( level.base[block.y][ty] == TileLogicType.EMPTY ) {
                           action = "reborn"
                           alive = true
                           inHole = false
                           break
                       }
                   }
               }

               if ( !stopGuards ) {
                   updateGuard(runner!!)
               }
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
